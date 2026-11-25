package com.example.news.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.news.entity.NewsAnnotation;

public interface NewsAnnotationRepository extends JpaRepository<NewsAnnotation, Long> {
	List<NewsAnnotation> findByPdfDocumentIdOrderByPageNumberAscIdAsc(Long pdfDocumentId);

	void deleteByPdfDocumentId(Long pdfDocumentId);
}
