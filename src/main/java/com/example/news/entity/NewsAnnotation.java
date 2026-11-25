package com.example.news.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "news_annotations")
public class NewsAnnotation {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long pdfDocumentId;

	@Column(nullable = false)
	private Integer pageNumber;

	@Column(nullable = false)
	private Float pdfX;

	@Column(nullable = false)
	private Float pdfY;

	@Column(nullable = false)
	private Float pdfWidth;

	@Column(nullable = false)
	private Float pdfHeight;

	@Column(nullable = false, length = 200)
	private String newsTitle;

	protected NewsAnnotation() {
	}

	public NewsAnnotation(Long pdfDocumentId, Integer pageNumber, Float pdfX, Float pdfY, Float pdfWidth,
			Float pdfHeight, String newsTitle) {
		this.pdfDocumentId = pdfDocumentId;
		this.pageNumber = pageNumber;
		this.pdfX = pdfX;
		this.pdfY = pdfY;
		this.pdfWidth = pdfWidth;
		this.pdfHeight = pdfHeight;
		this.newsTitle = newsTitle;
	}

	public Long getId() {
		return id;
	}

	public Long getPdfDocumentId() {
		return pdfDocumentId;
	}

	public Integer getPageNumber() {
		return pageNumber;
	}

	public Float getPdfX() {
		return pdfX;
	}

	public Float getPdfY() {
		return pdfY;
	}

	public Float getPdfWidth() {
		return pdfWidth;
	}

	public Float getPdfHeight() {
		return pdfHeight;
	}

	public String getNewsTitle() {
		return newsTitle;
	}
}
