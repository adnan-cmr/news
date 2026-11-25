package com.example.news.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.UUID;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class PdfStorageService {

	private static final long MAX_FILE_SIZE = 50L * 1024 * 1024;
	private final Path uploadDirectory;

	public PdfStorageService(@Value("${app.upload.dir:./uploads}") String uploadDirectory) {
		this.uploadDirectory = Path.of(uploadDirectory).toAbsolutePath().normalize();
	}

	public StoredPdf store(MultipartFile file) throws IOException {
		if (file == null || file.isEmpty() || file.getSize() > MAX_FILE_SIZE) {
			throw new IllegalArgumentException("Select a PDF no larger than 50 MB.");
		}
		if (!"application/pdf".equalsIgnoreCase(file.getContentType())) {
			throw new IllegalArgumentException("The uploaded file must have PDF content type.");
		}

		String originalFilename = file.getOriginalFilename();
		if (originalFilename == null || originalFilename.isBlank() || originalFilename.length() > 255
				|| originalFilename.indexOf('/') >= 0 || originalFilename.indexOf('\\') >= 0
				|| originalFilename.chars().anyMatch(Character::isISOControl)) {
			throw new IllegalArgumentException("The PDF filename is invalid.");
		}
		if (!originalFilename.toLowerCase(java.util.Locale.ROOT).endsWith(".pdf")) {
			throw new IllegalArgumentException("The filename must end in .pdf.");
		}

		Files.createDirectories(uploadDirectory);
		try {
			Files.setPosixFilePermissions(uploadDirectory, PosixFilePermissions.fromString("rwx------"));
		} catch (UnsupportedOperationException ignored) {
			// POSIX permissions are not available on every supported filesystem.
		}

		String storedFilename = UUID.randomUUID() + ".pdf";
		Path target = resolveStoredFilename(storedFilename);
		try {
			try (InputStream input = file.getInputStream()) {
				Files.copy(input, target);
			}
		} catch (IOException exception) {
			try {
				Files.deleteIfExists(target);
			} catch (IOException cleanupException) {
				exception.addSuppressed(cleanupException);
			}
			throw exception;
		}

		try (PDDocument document = PDDocument.load(target.toFile())) {
			if (document.isEncrypted() || document.getNumberOfPages() < 1) {
				throw new IllegalArgumentException("The PDF must be readable and contain at least one page.");
			}
			return new StoredPdf(originalFilename, storedFilename, document.getNumberOfPages());
		} catch (IOException | RuntimeException exception) {
			Files.deleteIfExists(target);
			throw exception;
		}
	}

	public Path resolveStoredFilename(String storedFilename) {
		if (storedFilename == null || !storedFilename.matches("[0-9a-fA-F-]{36}\\.pdf")) {
			throw new IllegalArgumentException("Invalid stored PDF filename.");
		}
		Path target = uploadDirectory.resolve(storedFilename).normalize();
		if (!target.startsWith(uploadDirectory)) {
			throw new IllegalArgumentException("Invalid stored PDF path.");
		}
		return target;
	}

	public record StoredPdf(String originalFilename, String storedFilename, int pageCount) {
	}
}
