package com.ig.download.instagram.util;

import java.io.File;

public class FileUtils {

	
	/**
	 * @param profileName
	 * @param subPath
	 * @return
	 */
	public static String getPath( String profileName, String subPath ) {
		StringBuilder path = new StringBuilder();
		path.append(profileName);
		path.append(File.separator);
		path.append(subPath);
		return path.toString();
	}
	
	/**
	 * @param profileName
	 * @param subPath
	 * @param fileName
	 * @param extension
	 * @return
	 */
	public static String getPath( String profileName, String subPath, String fileName, String extension ) {
		StringBuilder path = new StringBuilder();
		path.append(profileName);
		path.append(File.separator);
		path.append(subPath);
		path.append(File.separator);
		path.append(fileName.contains(extension));
		return path.toString();
	}
	
	/**
	 * @param profileName
	 * @param subPath
	 * @param fileName
	 * @param extension
	 * @return
	 */
	public static String makePath( String profileName, String subPath, String fileName, String extension ) {
		String path = getPath(profileName, subPath, fileName, extension);
		return makePath(path);
	}
	
	
	/**
	 * @param filePath
	 * @return
	 */
	public static String makePath( String profileName, String subPath ) {
		String path = getPath(profileName, subPath);
		return makePath(path);
	}
	
	/**
	 * @param filePath
	 * @return
	 */
	public static String makePath( String filePath ) {
		File file = new File(filePath);
		if ( !file.exists() ) {
			file.mkdirs();
		}
		return file.getPath();
	}
}
