package com.ig.download.instagram;

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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import com.ig.download.instagram.constant.ExtensionConstant;
import com.ig.download.instagram.util.DateUtils;

import io.github.bonigarcia.wdm.WebDriverManager;

public class Test {

	public static void main(String[] args) throws Exception {
		/*String targetUrl = "https://www.instagram.com/__blue_lemon/p/DS2e90GEz3J/";
		String profileName = "lemon";
		Path directory = Paths.get(profileName, "photos");
		Files.createDirectories(directory);
		
		scrapLink(targetUrl, profileName ); */
		String caption = "flf fdsfs fdsfsdfsdfjf";
		if ( caption != null ) {
			String lastIndex = StringUtils.substring(caption,  caption.length() -1, caption.length() );
			if ( !".".equals(lastIndex)) {
				caption = caption.concat(".");
			}
		}
		System.out.println(caption);
	}
	
	
	private static void scrapLink( String targetUrl, String profileName ) throws Exception {
		
		Map<String, String> mobileEmulation = new HashMap<>();
		mobileEmulation.put("deviceName", "iPhone 12 Pro");
		ChromeOptions options = new ChromeOptions();
		options.addArguments("--headless");
		options.addArguments("--disable-gpu");
		options.addArguments("--no-sandbox");
		options.setExperimentalOption("mobileEmulation", mobileEmulation);
		options.addArguments("--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
		WebDriverManager.chromedriver().setup();
		WebDriver driver = new ChromeDriver(options);
		try {
			driver.get( targetUrl );
			Set<String> imageUrls = new LinkedHashSet<>();
			String caption = getCaption(driver);
			boolean isNext = true;
			while ( isNext ) {
				WebElement element = driver.findElement(By.tagName("div"));
				// Get all the elements available with tag name '_aagv'
				List<WebElement> imageEls = element.findElements(By.className("_aagv"));
				for (WebElement e : imageEls) {
					WebElement img = e.findElement(By.tagName("img"));
					imageUrls.add(img.getAttribute("src"));
				}
				// Use findElements to avoid NoSuchElementException
				try {
					WebElement nextButtons = driver.findElement(By.cssSelector("button[aria-label='Next']"));
					((JavascriptExecutor) driver).executeScript("arguments[0].click();", nextButtons);
				} catch ( Exception e) {
					isNext = false;
				}
			}
			List<Map<String, Object>> results = new ArrayList<Map<String, Object>>();
			int index = 1;
			for (String url : imageUrls) {
				// PROCESS DOWNLOAD IMAGE 
				Map<String, Object> result = new HashMap<String, Object>();
				result.put("caption", String.format("%s-(%s)", caption, index));
				result.put("uRL", url);
				index++;
				results.add(result);
				downloadImage(profileName, String.format("%s-(%s)", caption, index), url);
			}
			imageUrls.stream().forEach(System.out::println); 
		} finally {
			driver.quit();
		}
	}
	
	
	/**
	 * @param driver
	 * @return
	 */
	private static String getCaption( WebDriver driver ) {
		String caption = "";
		WebElement article = driver.findElement(By.tagName("article"));
		List<WebElement> spans= article.findElements(By.className("_ap3a"));
		for ( WebElement span: spans ) {
			try {
				WebElement div = span.findElement(By.tagName("div"));
				WebElement h1 = div.findElement(By.tagName("h1"));
				caption = h1.getText();
			} catch (Exception e) {
			}
		}
		
		if ( caption.isBlank() ) {
			caption = DateUtils.getCurrentFormatDate(DateUtils.FORMAT_FULL_DATETIME);
		}
		
		return caption;
	}
	
	
	private static void downloadImage( String profileName, String caption, String imageUrl ) throws Exception {
		
		caption = safeCaption(caption);
		Path finalPath = Paths.get(profileName, "photos", caption + ExtensionConstant.JPG);
		URL url = new URL(imageUrl);
		HttpURLConnection connection = (HttpURLConnection) url.openConnection();
		connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
		connection.setConnectTimeout(10000);
		connection.setReadTimeout(10000);
		connection.connect();
		try (InputStream in = connection.getInputStream(); OutputStream out = Files.newOutputStream(finalPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
			byte[] buffer = new byte[8192];
			int bytesRead;
			while ((bytesRead = in.read(buffer)) != -1) {
				out.write(buffer, 0, bytesRead);
			}
		}
		connection.disconnect();
		
	}
	
	private static String safeCaption(String caption) {
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

}
