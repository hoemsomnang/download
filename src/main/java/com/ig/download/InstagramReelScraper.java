package com.ig.download;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.devtools.DevTools;
import org.openqa.selenium.devtools.v144.network.Network;
import org.openqa.selenium.devtools.v144.network.model.Response;

public class InstagramReelScraper {

	public static void main(String[] args) throws InterruptedException {

		WebDriver driver = new ChromeDriver();
		DevTools devTools = ((ChromeDriver) driver).getDevTools();
		devTools.createSession();

		devTools.send(Network.enable(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()));

		
		Map<String, String> requestMap = new HashMap<>();
		// Capture HTML response
		devTools.addListener(Network.responseReceived(), response -> {
			Response res = response.getResponse();
			if (res.getMimeType().contains("html")) {
				requestMap.put(response.getRequestId().toString(), res.getUrl());
			}
		});

		// When loading finished
		devTools.addListener(Network.loadingFinished(), loading -> {

			String requestId = loading.getRequestId().toString();
			
			if (requestMap.containsKey(requestId)) {
				try {
					var body = devTools.send(Network.getResponseBody(loading.getRequestId()));
					String html = body.getBody();
					// Scrap Caption
					
					if (html.contains("representations")) {
						String json = extractJsonFromHtml(html);
						JSONObject root = new JSONObject(json);
						JSONArray reps = findRepresentations(root, "representations" ); 
						processDashJson(reps, driver);
					} 

				} catch (Exception e) {
					e.printStackTrace();
					System.out.println("Cannot fetch body.");
				}
			}
		});

		driver.get("https://www.instagram.com/long_manin/reel/DUp15C1CDrR/");
		Thread.sleep(15000);
	}

	public static String getCaption(WebDriver driver) {

        JavascriptExecutor js = (JavascriptExecutor) driver;

        String caption = (String) js.executeScript("""
            function extractCaption() {

                // 1️⃣ Try JSON-LD (best source)
                const ld = document.querySelector('script[type="application/ld+json"]');
                if (ld) {
                    try {
                        const data = JSON.parse(ld.textContent);
                        const obj = Array.isArray(data) ? data[0] : data;
                        if (obj.caption) return obj.caption;
                        if (obj.description) return obj.description;
                    } catch(e){}
                }

                // 2️⃣ Fallback to og:description
                const og = document.querySelector("meta[property='og:description']");
                if (og && og.content) {
                    let text = og.content;

                    // Remove "username on Instagram: "
                    const index = text.indexOf(":");
                    if (index !== -1) {
                        text = text.substring(index + 1);
                    }

                    return text.trim().replace(/^"|"$|^“|”$/g, "");
                }

                return null;
            }
            return extractCaption();
        """);

        return caption == null ? "No caption found" : caption.trim();
	}
	
	public static String extractJsonFromHtml(String html) {

		Document doc = Jsoup.parse(html);

		for (Element script : doc.select("script")) {
			String data = script.html();

			if (data.contains("all_video_dash_prefetch_representations")) {

				int start = data.indexOf("{");
				int end = data.lastIndexOf("}");

				if (start != -1 && end != -1) {
					return data.substring(start, end + 1);
				}
			}
		}

		return null;
	}

	public static JSONArray findRepresentations(Object obj, String keyName ) {

		if (obj instanceof JSONObject jsonObj) {

			for (String key : jsonObj.keySet()) {

				if (key.equals( keyName )) {
					return jsonObj.getJSONArray(key);
				}

				Object child = jsonObj.get(key);

				JSONArray result = findRepresentations(child, keyName );
				if (result != null)
					return result;
			}
		}

		if (obj instanceof JSONArray arr) {
			for (int i = 0; i < arr.length(); i++) {
				JSONArray result = findRepresentations(arr.get(i), keyName );
				if (result != null)
					return result;
			}
		}

		return null;
	}
	
	
	public static void extracCaption(JSONArray representations, WebDriver driver) throws Exception {

		try {
			String text = "";
			for (int i = 0; i < representations.length(); i++) {
				JSONObject rep = representations.getJSONObject(i);
				text = rep.getString("text");
				
			}
			System.out.println(text);
		} catch (Exception e) {
			e.printStackTrace();
			throw e;
		}
	}

	// 🔥 DASH Processor
	public static void processDashJson(JSONArray representations, WebDriver driver) throws Exception {

		try {
			String caption = getCaption(driver);
			String bestVideoUrl = null;
			int bestVideoBandwidth = 0;

			String bestAudioUrl = null;
			int bestAudioBandwidth = 0;

			for (int i = 0; i < representations.length(); i++) {

				JSONObject rep = representations.getJSONObject(i);

				String mime = rep.getString("mime_type");
				int bandwidth = rep.getInt("bandwidth");

				String url = rep.getString("base_url").replace("\\/", "/").replace("\\u00253D", "%3D");

				if (mime.equals("video/mp4") && bandwidth > bestVideoBandwidth) {
					bestVideoBandwidth = bandwidth;
					bestVideoUrl = url;
				}

				if (mime.equals("audio/mp4") && bandwidth > bestAudioBandwidth) {
					bestAudioBandwidth = bandwidth;
					bestAudioUrl = url;
				}
			}

			System.out.println("🎥 Best Video Bandwidth: " + bestVideoBandwidth);
			System.out.println("🔊 Best Audio Bandwidth: " + bestAudioBandwidth);

			if (bestVideoUrl != null && bestAudioUrl != null) {
				downloadFile(bestVideoUrl, "video.mp4", driver);
				downloadFile(bestAudioUrl, "audio.mp4", driver);

				merge("video.mp4", "audio.mp4", caption.concat(".mp4"));

				System.out.println("✅ Final file ready: final.mp4");
			}
		} catch (Exception e) {
			e.printStackTrace();
			throw e;
		}
	}

	public static void downloadFile(String fileUrl, String saveName, WebDriver driver) {

		try {
			HttpURLConnection conn = (HttpURLConnection) new URL(fileUrl).openConnection();

			conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
			conn.setRequestProperty("Accept", "*/*");
			conn.setRequestProperty("Referer", "https://www.instagram.com/");
			conn.setRequestProperty("Origin", "https://www.instagram.com");
			conn.setConnectTimeout(15000);
			conn.setReadTimeout(15000);

			System.out.println("⬇ Start Download: " + saveName);
			try (InputStream in = conn.getInputStream(); FileOutputStream out = new FileOutputStream(saveName)) {

				byte[] buffer = new byte[8192];
				int read;
				while ((read = in.read(buffer)) != -1) {
					out.write(buffer, 0, read);
				}
			}
			System.out.println("✅ Downloaded: " + saveName);

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static void merge(String video, String audio, String output) throws Exception {

		ProcessBuilder pb = new ProcessBuilder("ffmpeg", "-y", "-fflags", "+genpts", "-i", video, "-i", audio, "-c:v", "copy", "-c:a", "aac", "-shortest", output);

		pb.inheritIO();
		Process process = pb.start();
		process.waitFor();

		// REMOVE VDO & AUDIO 
		File vdoToDelete = new File( video );
		boolean vdo = vdoToDelete.delete();
		if ( !vdo ) {
			throw new Exception( "Merge VDO Error" );
		}
		File audioToDelete = new File( audio );
		boolean audioSuccess = audioToDelete.delete();
		if ( !audioSuccess ) {
			throw new Exception( "Merge VDO Error" );
		}
		System.out.println("🎬 Merged successfully!");
	}
}
