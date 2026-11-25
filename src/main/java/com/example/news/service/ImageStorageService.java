package com.example.news.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ImageStorageService {

	private static final long MAX_IMAGE_SIZE = 5L * 1024 * 1024;
	private static final Map<String, String> EXTENSIONS = Map.of(
			"image/jpeg", "jpg", "image/png", "png", "image/gif", "gif");
	private final Path imageDirectory;

	public ImageStorageService(@Value("${app.upload.dir:./uploads}") String uploadDirectory) {
		this.imageDirectory = Path.of(uploadDirectory).toAbsolutePath().normalize().resolve("images");
	}

	public StoredImage store(MultipartFile file) throws IOException {
		if (file == null || file.isEmpty() || file.getSize() > MAX_IMAGE_SIZE) {
			throw new IllegalArgumentException("Select an image no larger than 5 MB.");
		}
		String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
		String extension = EXTENSIONS.get(contentType);
		if (extension == null) {
			throw new IllegalArgumentException("The image must be a JPEG, PNG or GIF file.");
		}
		try (InputStream input = file.getInputStream()) {
			if (ImageIO.read(input) == null) {
				throw new IllegalArgumentException("The uploaded file is not a valid image.");
			}
		}
		Files.createDirectories(imageDirectory);
		String filename = UUID.randomUUID() + "." + extension;
		try (InputStream input = file.getInputStream()) {
			Files.copy(input, resolve(filename));
		}
		return new StoredImage(filename, contentType);
	}

	public Path resolve(String filename) {
		if (filename == null || !filename.matches("[0-9a-fA-F-]{36}\\.(jpg|png|gif)")) {
			throw new IllegalArgumentException("Invalid image filename.");
		}
		Path target = imageDirectory.resolve(filename).normalize();
		if (!target.startsWith(imageDirectory)) {
			throw new IllegalArgumentException("Invalid image path.");
		}
		return target;
	}

	public void delete(String filename) throws IOException {
		Files.deleteIfExists(resolve(filename));
	}

	public record StoredImage(String filename, String contentType) {
	}
}
