package com.ig.download.instagram.service.v1;

import java.util.List;
import java.util.Map;

public interface InstagramScrapPhotoLinkService {
	public List<Map<String, Object>> scrapPhotos( String targetUrl ) throws Exception;
}
