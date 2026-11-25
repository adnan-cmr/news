package com.example.news.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import com.example.news.entity.PdfDocument;
import com.example.news.repository.PdfDocumentRepository;
import com.example.news.service.PdfStorageService;

@RestController
public class PdfStreamController {

	private final PdfDocumentRepository documents;
	private final PdfStorageService storage;

	public PdfStreamController(PdfDocumentRepository documents, PdfStorageService storage) {
		this.documents = documents;
		this.storage = storage;
	}

	@GetMapping("/pdf/view/{pdfId}")
	public ResponseEntity<Resource> stream(@PathVariable Long pdfId, Authentication authentication) throws IOException {
		PdfDocument document = documents.findById(pdfId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "PDF not found."));
		boolean isAdmin = authentication != null && authentication.getAuthorities().stream()
				.anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
		if (!Boolean.TRUE.equals(document.getPublished()) && !isAdmin) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "PDF not found.");
		}

		Path path = storage.resolveStoredFilename(document.getStoredFilename());
		if (!Files.isRegularFile(path)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "PDF file not found.");
		}
		Resource resource = new FileSystemResource(path);
		return ResponseEntity.ok()
				.contentType(MediaType.APPLICATION_PDF)
				.header(HttpHeaders.CONTENT_DISPOSITION,
						ContentDisposition.inline().filename(document.getStoredFilename()).build().toString())
				.header("X-Content-Type-Options", "nosniff")
				.body(resource);
	}
}
