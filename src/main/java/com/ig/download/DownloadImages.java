package com.ig.download;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.*;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.openqa.selenium.By;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import io.github.bonigarcia.wdm.WebDriverManager;

public class DownloadImages {

	public static void main(String[] args) {

		String profileName = "langimna";
		String profileURL = "https://www.instagram.com/theophaniapk?igsh=amdldnFhbWpxdmUy";
		// Scrap Profile Link
		Set<String> linkScrap = scrapProfile(profileURL);
		for (String link : linkScrap) {
			// Scrap Photo By Link
			List<Map<String, Object>> results = scrapPhoto(link);

			for (Map<String, Object> dataInfo : results) {
				try {
					Path savePath = buildPath(profileName, String.valueOf(dataInfo.get("Caption")), ".jpg");
					downloadImage(String.valueOf(dataInfo.get("Url")), savePath);
					System.out.println("Image downloaded successfully to: " + savePath.toAbsolutePath());
				} catch (Exception e) {
					System.err.println("Failed to download image: " + e.getMessage());
				}
			}
		}
	}

	public static Set<String> scrapProfile(String profileUrl) {
		ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
		// Consider headless for stability in CI:
		// options.addArguments("--headless=new");

		WebDriverManager.chromedriver().setup();
		WebDriver driver = new ChromeDriver(options);

		// Use LinkedHashSet to keep order and uniqueness
		Set<String> postLinks = new LinkedHashSet<>();

		try {
			driver.get("https://www.instagram.com");
			loadCookies(driver, "D://03.IG/cookies.txt");
			driver.navigate().refresh(); // refresh after setting cookies to apply session
			driver.get(profileUrl);
			WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));
			// Wait for the grid to appear (grid items are <a> with /p/ or /reel/)
			wait.until(d -> d.findElements(By.cssSelector("a[href*='/p/'], a[href*='/reel/']")).size() > 0);

			final int MAX_SCROLLS = 200; // hard cap
			final int MAX_STALE_ROUNDS = 5; // how many consecutive "no change" rounds before giving up
			final int SLEEP_AFTER_SCROLL_MS = 1500;

			int staleRounds = 0;
			long lastHeight = getScrollHeight(driver);
			int lastCount = 0;

			for (int i = 0; i < MAX_SCROLLS; i++) {
				// Collect current anchors
				addVisiblePostLinks(driver, postLinks);

				// Scroll the last tile into view (helps trigger lazy loading more reliably than
				// page scroll)
				scrollLastTileIntoView(driver);

				// Small wait to let new content load
				Thread.sleep(SLEEP_AFTER_SCROLL_MS);

				// Re-collect
				addVisiblePostLinks(driver, postLinks);

				// Check conditions
				long currentHeight = getScrollHeight(driver);
				int currentCount = postLinks.size();

				boolean heightUnchanged = currentHeight == lastHeight;
				boolean countUnchanged = currentCount == lastCount;

				if (heightUnchanged && countUnchanged) {
					staleRounds++;
				} else {
					staleRounds = 0; // reset if we made progress
				}

				// Debug info (optional)
				System.out.printf("Scroll %d → height: %d (Δ %d), posts: %d (Δ %d), staleRounds: %d%n", i + 1,
						currentHeight, (currentHeight - lastHeight), currentCount, (currentCount - lastCount),
						staleRounds);

				lastHeight = currentHeight;
				lastCount = currentCount;

				// If we've seen no change for several rounds, assume end of feed
				if (staleRounds >= MAX_STALE_ROUNDS) {
					System.out.println("Reached end of profile feed (no more growth).");
					break;
				}
			}

			// Output
			postLinks.forEach(System.out::println);

		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			driver.quit();
		}

		return postLinks;
	}

	private static void addVisiblePostLinks(WebDriver driver, Set<String> postLinks) {
		List<WebElement> anchors = driver.findElements(By.cssSelector("a[href*='/p/'], a[href*='/reel/']"));
		for (WebElement a : anchors) {
			try {
				String href = a.getAttribute("href");
				if (href != null && (href.contains("/p/") || href.contains("/reel/"))) {
					// Normalize href by trimming query params/fragments if desired
					// href = href.split("\\?")[0];
					postLinks.add(href);
				}
			} catch (StaleElementReferenceException ignored) {
				// Skip stale elements; they can occur during dynamic updates
			}
		}
	}

	private static void scrollLastTileIntoView(WebDriver driver) {
		List<WebElement> anchors = driver.findElements(By.cssSelector("a[href*='/p/'], a[href*='/reel/']"));
		if (!anchors.isEmpty()) {
			WebElement last = anchors.get(anchors.size() - 1);
			((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block: 'center'});", last);
		} else {
			// fallback: page scroll
			((JavascriptExecutor) driver).executeScript("window.scrollBy(0, 1500);");
		}
	}

	private static long getScrollHeight(WebDriver driver) {
		Object height = ((JavascriptExecutor) driver).executeScript("return document.body.scrollHeight;");
		if (height instanceof Long)
			return (Long) height;
		if (height instanceof Number)
			return ((Number) height).longValue();
		return -1L;
	}

	private static void loadCookies(WebDriver driver, String filePath) {
		try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
			String line;
			while ((line = br.readLine()) != null) {
				if (line.startsWith("#") || line.trim().isEmpty())
					continue;

				String[] tokens = line.split("\t");
				if (tokens.length >= 7) {
					// Netscape cookie format: domain, flag, path, secure, expiry, name, value
					String domain = tokens[0];
					String path = tokens[2];
					String name = tokens[5];
					String value = tokens[6];

					Cookie cookie = new Cookie.Builder(name, value).domain(domain).path(path).isHttpOnly(false)
							.isSecure(false).build();
					driver.manage().addCookie(cookie);
				}
			}
			System.out.println("Cookies injected successfully!");
		} catch (Exception e) {
			System.err.println("Error loading cookies: " + e.getMessage());
		}
	}

	public static List<Map<String, Object>> scrapPhoto(String targetUrl) {

		ChromeOptions options = new ChromeOptions();
		options.addArguments("--headless");
		options.addArguments("--disable-gpu");
		options.addArguments("--no-sandbox");
		options.addArguments("--window-size=1920,1080");
		// Crucial: Set a real User-Agent to avoid the "blob" restriction and bot
		// detection
		options.addArguments(
				"--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");

		WebDriverManager.chromedriver().setup();
		WebDriver driver = new ChromeDriver(options);
		Set<String> mediaUrls = new LinkedHashSet<>();
		List<Map<String, Object>> results = new ArrayList<Map<String, Object>>();
		try {

			driver.get(targetUrl);

			WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));

			// Wait for the main article or video to load
			wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("body")));

			boolean hasNext = true;
			String caption = getCaption(driver);
			while (hasNext) {
				Thread.sleep(2000); // Wait for transition

				try {
					WebElement img = driver.findElement(By.xpath("//div[@class='_aagv']//img"));
					mediaUrls.add(img.getAttribute("src"));
				} catch (NoSuchElementException ignored) {
				}
				// NAVIGATION: Click 'Next'
				try {
					// Instagram uses 'aria-label' for navigation buttons
					WebElement nextBtn = driver.findElement(By.xpath("//button[@aria-label='Next']"));
					((JavascriptExecutor) driver).executeScript("arguments[0].click();", nextBtn);
				} catch (NoSuchElementException e) {
					hasNext = false; // End of carousel
				}
			}

			System.out.println("\n--- Found " + mediaUrls.size() + " Direct Links ---");
			int index = 1;
			for (String url : mediaUrls) {
				Map<String, Object> result = new HashMap<String, Object>();
				result.put("Caption", String.format("%s-(%s)", caption, index));
				result.put("Url", url);
				index++;
				results.add(result);
			}
			System.out.println(results);
		} catch (Exception e) {
			System.err.println("Fatal Error: " + e.getMessage());
		} finally {
			driver.quit();
		}

		return results;
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

	// 🔥 Build safe path
	public static Path buildPath(String profileName, String fileName, String extension) throws IOException {

		// Remove invalid filename characters
		String safeFileName = fileName.replaceAll("[\\\\/:*?\"<>|]", "_");

		Path directory = Paths.get(profileName);

		// Create directory if not exists
		if (!Files.exists(directory)) {
			Files.createDirectories(directory);
		}

		return directory.resolve(safeFileName + extension);
	}

	public static void downloadImage(String imageUrl, Path savePath) throws IOException {

		URL url = new URL(imageUrl);
		HttpURLConnection connection = (HttpURLConnection) url.openConnection();

		connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
		connection.setConnectTimeout(10000);
		connection.setReadTimeout(10000);

		connection.connect();

		try (InputStream in = connection.getInputStream();
				OutputStream out = Files.newOutputStream(savePath, StandardOpenOption.CREATE,
						StandardOpenOption.TRUNCATE_EXISTING)) {

			byte[] buffer = new byte[8192];
			int bytesRead;

			while ((bytesRead = in.read(buffer)) != -1) {
				out.write(buffer, 0, bytesRead);
			}
		}

		connection.disconnect();
	}
}
