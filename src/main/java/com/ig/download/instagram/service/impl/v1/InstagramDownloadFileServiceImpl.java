package com.ig.download.instagram.service.impl.v1;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
import org.springframework.stereotype.Service;

import com.ig.download.instagram.constant.ExtensionConstant;
import com.ig.download.instagram.service.v1.InstagramDownloadFileService;

@Service
public class InstagramDownloadFileServiceImpl implements InstagramDownloadFileService {

		
	@Override
	public void downloadPhotos(String imageUrl, String caption, String profileName ) throws Exception {
		try {
			caption = safeCaption(caption);
			Path savePath = buildPath(profileName, caption, ExtensionConstant.JPG);
			downloadImage(imageUrl, savePath);
			System.out.println("Image downloaded successfully to: " + savePath.toAbsolutePath());
		} catch (Exception e) {
			System.err.println("Failed to download image: " + e.getMessage());
		}
	}

	public static String safeCaption(String caption) {
	    if (caption == null || caption.isBlank())
	        return "video_" + System.currentTimeMillis();
		// -------- Extract hashtags --------
		Matcher matcher = Pattern.compile("#\\w+").matcher(caption);
		List<String> tags = new ArrayList<>();
		while (matcher.find() && tags.size() < 3) {
			tags.add(matcher.group());
		}
		// remove all hashtags from caption text
		String textOnly = caption.replaceAll("#\\w+", "").trim();
		// rebuild caption: text + 3 hashtags
		String rebuilt = textOnly + " " + String.join(" ", tags);
		// remove only illegal filename chars (\ / : * ? " < > |)
		rebuilt = rebuilt.replaceAll("[\\\\/:*?\"<>|]", "");
		// collapse multiple spaces
		rebuilt = rebuilt.replaceAll("\\s+", " ").trim();

		// limit length to 100 chars
		if (rebuilt.length() > 100)
			rebuilt = rebuilt.substring(0, 100);

		if (rebuilt.isBlank())
			rebuilt = "video_" + System.currentTimeMillis();

		return rebuilt;
	}
	// 🔥 Build safe path
	/**
	 * @param profileName
	 * @param fileName
	 * @param extension
	 * @return
	 * @throws IOException
	 */
	public Path buildPath(String profileName, String fileName, String extension) throws IOException {

		// Remove invalid filename characters
		String safeFileName = fileName.replaceAll("[\\\\/:*?\"<>|]", "_");

		Path directory = Paths.get(profileName, "photos");

		// Create directory if not exists
		if (!Files.exists(directory)) {
			Files.createDirectories(directory);
		}

		return directory.resolve(safeFileName + extension);
	}

	/**
	 * @param imageUrl
	 * @param savePath
	 * @throws IOException
	 */
	public void downloadImage(String imageUrl, Path savePath) throws IOException {

		URL url = new URL(imageUrl);
		HttpURLConnection connection = (HttpURLConnection) url.openConnection();
		connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
		connection.setConnectTimeout(10000);
		connection.setReadTimeout(10000);
		connection.connect();
		try (InputStream in = connection.getInputStream(); OutputStream out = Files.newOutputStream(savePath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
			byte[] buffer = new byte[8192];
			int bytesRead;
			while ((bytesRead = in.read(buffer)) != -1) {
				out.write(buffer, 0, bytesRead);
			}
		}
		connection.disconnect();
	}

	/**
	 * @param downloadReel
	 * @param url
	 * @throws IOException
	 */
	@Override
	public void downloadReel(String url, String profileName, DevTools devTools, ChromeDriver driver ) throws Exception {
		try {
			
			/*ChromeOptions options = new ChromeOptions();
			options.addArguments("--headless=new");
			options.addArguments("--disable-gpu");
			options.addArguments("--no-sandbox");
			options.addArguments("--disable-dev-shm-usage");
			options.addArguments("--window-size=1920,1080");
			options.addArguments("--disable-blink-features=AutomationControlled");

			ChromeDriver driver = new ChromeDriver(options);
			DevTools devTools = ((ChromeDriver) driver).getDevTools();
			devTools.createSession();
			devTools.send(Network.enable(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()));*/

			// Prepare wait mechanism to avoid Thread.sleep
			CountDownLatch latch = new CountDownLatch(1);
			AtomicReference<String> htmlRef = new AtomicReference<>();

			Map<String, String> requestMap = new HashMap<>();
			// Capture HTML response
			devTools.addListener(Network.responseReceived(), response -> {
				Response res = response.getResponse();
				// Track HTML documents only
				if (res.getMimeType() != null && res.getMimeType().contains("html")) {
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
						if (html != null && html.contains("representations")) {
							htmlRef.set(html);
							latch.countDown();
						}
					} catch (Exception e) {
						e.printStackTrace();
						System.out.println("Cannot fetch body.");
					}
				}
			});
			driver.get(url);
			// Wait up to 10 seconds for the HTML that contains the dash representations
			boolean ok = latch.await(10, TimeUnit.SECONDS);
			// VERY IMPORTANT: remove listeners to avoid memory leaks
			devTools.clearListeners();
			if (!ok) {
				throw new RuntimeException("Timeout waiting for Instagram DASH JSON in HTML");
			}
			String html = htmlRef.get();
			String json = extractJsonFromHtml(html);
			if (json == null) {
				throw new RuntimeException("Could not extract DASH JSON from HTML");
			}
			JSONObject root = new JSONObject(json);
			JSONArray reps = findRepresentations(root, "representations");
			if (reps == null || reps.isEmpty()) {
				throw new RuntimeException("No DASH 'representations' found");
			}
			processDashJson(reps, driver, profileName);
		} catch( Exception e ) {
			throw e;
		}

	}
	
	/**
	 * @param driver
	 * @return
	 */
	public String getCaption(WebDriver driver) {

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
	
	/**
	 * @param html
	 * @return
	 */
	public String extractJsonFromHtml(String html) {

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

	/**
	 * @param obj
	 * @param keyName
	 * @return
	 */
	public JSONArray findRepresentations(Object obj, String keyName ) {

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
	

	// 🔥 DASH Processor
	/**
	 * @param representations
	 * @param driver
	 * @param profileName
	 * @throws Exception
	 */
	public void processDashJson(JSONArray representations, WebDriver driver, String profileName ) throws Exception {

		try {
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

			if (bestVideoUrl != null && bestAudioUrl != null) {
				String caption = safeCaption(getCaption(driver));
				Path finalPath = Paths.get(profileName, "VDO", caption + ExtensionConstant.MP4);
				Map<String, String> ffHeaders = buildCdnHeaders(driver);
				mergeFromUrls(bestVideoUrl, bestAudioUrl, finalPath, ffHeaders);
				System.out.println("✅ Final file ready: " + finalPath);

			}
		} catch (Exception e) {
			e.printStackTrace();
			throw e;
		}
	}

	/**
	 * @param driver
	 * @return
	 */
	private Map<String, String> buildCdnHeaders(WebDriver driver) {
		Map<String, String> headers = new LinkedHashMap<>();
		headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
		headers.put("Accept", "*/*");
		headers.put("Referer", "https://www.instagram.com/");
		headers.put("Origin", "https://www.instagram.com");
		headers.put("Accept-Language", "en-US,en;q=0.9");

		// Optional: forward cookies from Selenium for private/locked content
		String cookieHeader = driver.manage().getCookies().stream().map(c -> c.getName() + "=" + c.getValue())
				.collect(Collectors.joining("; "));
		if (!cookieHeader.isBlank()) {
			headers.put("Cookie", cookieHeader);
		}
		return headers;
	}
	
	/**
	 * @param videoUrl
	 * @param audioUrl
	 * @param output
	 * @param headers
	 * @throws Exception
	 */
	private void mergeFromUrls(String videoUrl, String audioUrl, Path output, Map<String, String> headers)
			throws Exception {
		Files.createDirectories(output.getParent());

		// Build headers string (CRLF-separated) for ffmpeg
		String hdr = headers.entrySet().stream().map(e -> e.getKey() + ": " + e.getValue())
				.collect(Collectors.joining("\r\n"));

		List<String> cmd = new ArrayList<>(List.of("ffmpeg", "-y", "-loglevel", "error", "-nostdin",
				// headers for both inputs (ffmpeg requires them per input)
				"-headers", hdr, "-i", videoUrl, "-headers", hdr, "-i", audioUrl,
				// Try pure stream copy for speed; if it fails, fallback in catch
				"-c:v", "copy", "-c:a", "copy", "-shortest", "-movflags", "+faststart", output.toString()));

		ProcessBuilder pb = new ProcessBuilder(cmd);
		pb.redirectErrorStream(true); // collapse stderr/stdout
		Process p = pb.start();
		int exit = p.waitFor();

		if (exit != 0) {
			// Fallback: try re-encoding audio only (some IG audios are AAC already, some
			// may need aac)
			List<String> fallback = new ArrayList<>(List.of("ffmpeg", "-y", "-loglevel", "error", "-nostdin",
					"-headers", hdr, "-i", videoUrl, "-headers", hdr, "-i", audioUrl, "-c:v", "copy", "-c:a", "aac",
					"-b:a", "192k", "-shortest", "-movflags", "+faststart", output.toString()));
			ProcessBuilder pb2 = new ProcessBuilder(fallback);
			pb2.redirectErrorStream(true);
			int exit2 = pb2.start().waitFor();
			if (exit2 != 0) {
				throw new IOException("ffmpeg failed (even fallback). Exit codes: " + exit + ", " + exit2);
			}
		}
	}


}
