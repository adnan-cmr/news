package com.example.news.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.news.entity.PdfDocument;

public interface PdfDocumentRepository extends JpaRepository<PdfDocument, Long> {
	List<PdfDocument> findAllByOrderByUploadedAtDesc();

	List<PdfDocument> findByPublishedTrueOrderByUploadedAtDesc();

	Optional<PdfDocument> findFirstByPublishedTrueOrderByUploadedAtDesc();
}
