package com.ig.download.instagram.service.v1;

import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.devtools.DevTools;

public interface InstagramDownloadFileService {

	public void downloadPhotos( String url, String caption, String profileName ) throws Exception;
	
	public void downloadReel( String url, String profileName, DevTools devTools, ChromeDriver driver ) throws Exception;
}
