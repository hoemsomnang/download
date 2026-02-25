package com.ig.download.instagram.service.v1;

import java.util.Set;

public interface InstagramProfileScaperService {

	public Set<String> scrapProfile( String profileName ) throws Exception;
	
}
