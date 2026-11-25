package com.example.news.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "pdf_documents")
public class PdfDocument {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 255)
	private String originalFilename;

	@Column(nullable = false, unique = true, length = 40)
	private String storedFilename;

	@Column(nullable = false)
	private LocalDateTime uploadedAt;

	private Integer pageCount;

	@Column(nullable = false)
	private Boolean published = false;

	protected PdfDocument() {
	}

	public PdfDocument(String originalFilename, String storedFilename, LocalDateTime uploadedAt, Integer pageCount) {
		this.originalFilename = originalFilename;
		this.storedFilename = storedFilename;
		this.uploadedAt = uploadedAt;
		this.pageCount = pageCount;
		this.published = false;
	}

	public Long getId() {
		return id;
	}

	public String getOriginalFilename() {
		return originalFilename;
	}

	public String getStoredFilename() {
		return storedFilename;
	}

	public LocalDateTime getUploadedAt() {
		return uploadedAt;
	}

	public Integer getPageCount() {
		return pageCount;
	}

	public Boolean getPublished() {
		return published;
	}

	public void setPublished(Boolean published) {
		this.published = published;
	}
}
