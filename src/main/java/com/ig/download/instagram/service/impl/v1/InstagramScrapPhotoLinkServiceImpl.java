package com.ig.download.instagram.service.impl.v1;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.stereotype.Service;

import com.ig.download.instagram.service.v1.InstagramScrapPhotoLinkService;

import io.github.bonigarcia.wdm.WebDriverManager;

@Service
public class InstagramScrapPhotoLinkServiceImpl implements InstagramScrapPhotoLinkService {

	@Override
	public List<Map<String, Object>> scrapPhotos(String targetUrl) throws Exception {

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
				result.put("caption", String.format("%s-(%s)", caption, index));
				result.put("uRL", url);
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

}
