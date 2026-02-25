package com.ig.download.instagram.controller.v1;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.devtools.DevTools;
import org.openqa.selenium.devtools.v144.network.Network;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.ig.download.instagram.service.v1.InstagramDownloadFileService;
import com.ig.download.instagram.service.v1.InstagramProfileScaperService;
import com.ig.download.instagram.service.v1.InstagramScrapPhotoLinkService;

@RestController
@ResponseBody
@RequestMapping("/instagram/v1")
public class InstagramDownloadControllerV1 {
	
	@Autowired
	private InstagramDownloadFileService instagramDownloadFileService;
	@Autowired
	private InstagramProfileScaperService instagramProfileScaperService;
	@Autowired
	private InstagramScrapPhotoLinkService instagramScrapPhotoLinkService;
	
	@PostMapping("/videos")
	public void downloadReel( @RequestBody Map<String, Object> body ) throws Exception {
		try {
			String profileName = "long_long";
			String url = "https://www.instagram.com/long_manin/reel/DUp15C1CDrR/";
			//instagramDownloadFileService.downloadReel(url, profileName);
		} catch (Exception e) {
			throw e;
		}
		
	}
	
	@PostMapping("/photos")
	public void downloadPhotos( @RequestBody Map<String, Object> body ) throws Exception {
		try {
			
			String profileName = "long_long";
			String targetURL = "https://www.instagram.com/long_manin/p/DUnWmuOE48i/";
			List<Map<String, Object>> photos =  instagramScrapPhotoLinkService.scrapPhotos(targetURL);
			for ( Map<String, Object> photo : photos ) {
				instagramDownloadFileService.downloadPhotos(photo.get("uRL").toString(), photo.get("caption").toString() , profileName);
			}
			
		} catch (Exception e) {
			throw e;
		}
	}
	
	
	@PostMapping("/profile")
	public void downloadProfile( @RequestBody Map<String, Object> body ) throws Exception {
		try {
			
			String profileName = "b_girl_superlina";
			
			Set<String> urls = instagramProfileScaperService.scrapProfile(profileName);
			
			ChromeOptions options = new ChromeOptions();
			options.addArguments("--headless=new");
			options.addArguments("--disable-gpu");
			options.addArguments("--no-sandbox");
			options.addArguments("--disable-dev-shm-usage");
			options.addArguments("--window-size=1920,1080");
			options.addArguments("--disable-blink-features=AutomationControlled");

			ChromeDriver driver = new ChromeDriver(options);
			DevTools devTools = ((ChromeDriver) driver).getDevTools();
			devTools.createSession();
			devTools.send(Network.enable(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()));
			
			// Create directory follow profile Name
			Path directory = Paths.get(profileName,"VDO");
			Files.createDirectories(directory); // safe even if exists
			for ( String url : urls ) {
				if ( url.contains("reel")) {
					instagramDownloadFileService.downloadReel(url, profileName, devTools, driver );
				} else {
					List<Map<String, Object>> photos =  instagramScrapPhotoLinkService.scrapPhotos(url);
					for ( Map<String, Object> photo : photos ) {
						instagramDownloadFileService.downloadPhotos(photo.get("uRL").toString(), photo.get("caption").toString() , profileName);
					}
				}
			}
			
			 driver.quit();
			 
		} catch (Exception e) {
			throw e;
		}
	}
	
	
}
