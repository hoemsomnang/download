package com.ig.download;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.*;

public class DownloadImage {

	public static void main(String[] args) {


		String imageUrl = "https://instagram.fpnh11-1.fna.fbcdn.net/v/t39.30808-6/633722143_2075421199906953_6695144900242248265_n.jpg?stp=c0.64.1536.1920a_dst-jpg_e35_tt6&_nc_cat=102&ig_cache_key=MzgzMDEyOTQwODk0NzI0MTMxMw%3D%3D.3-ccb7-5&ccb=7-5&_nc_sid=58cdad&efg=eyJ2ZW5jb2RlX3RhZyI6InhwaWRzLjE1MzZ4MjA0OC5zZHIuQzMifQ%3D%3D&_nc_ohc=S8WWJFapQ8UQ7kNvwHpzzJk&_nc_oc=AdnwUQuW6AVV8SOKuxK-6--O51d9xn4ykix4DxLWhm1B-mAOUoSaCPgpic3I3-g14CQ&_nc_ad=z-m&_nc_cid=0&_nc_zt=23&_nc_ht=instagram.fpnh11-1.fna&_nc_gid=9ACwS5vWymUzjI8GmE-Jxw&oh=00_Afvxrboue1TpVbI-DqCKqdhwnjtjhe18j30EhsYoCnwG7Q&oe=6994D647";
		String caption = "downloaded_image";
		String profileName = "langimna";
		
		try {
			Path savePath = buildPath(profileName, caption, ".jpg");
			downloadImage(imageUrl, savePath);
			System.out.println("Image downloaded successfully to: " + savePath.toAbsolutePath());
		} catch (Exception e) {
			System.err.println("Failed to download image: " + e.getMessage());
		}
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
