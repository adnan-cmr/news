package com.example.news.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

@Entity
@Table(name = "news_posts")
public class NewsPost {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 60)
	private String category;

	@Column(nullable = false, length = 200)
	private String headingEn;

	@Column(nullable = false, length = 200)
	private String headingUr;

	@Column(nullable = false, length = 200)
	private String headingKn;

	@Lob
	@Column(nullable = false)
	private String bodyEn;

	@Lob
	@Column(nullable = false)
	private String bodyUr;

	@Lob
	@Column(nullable = false)
	private String bodyKn;

	@Column(nullable = false, length = 80)
	private String imageFilename;

	@Column(nullable = false, length = 100)
	private String imageContentType;

	@Column(nullable = false)
	private LocalDateTime createdAt;

	protected NewsPost() {
	}

	public NewsPost(String category, String headingEn, String headingUr, String headingKn, String bodyEn,
			String bodyUr, String bodyKn, String imageFilename, String imageContentType) {
		this.category = category;
		this.headingEn = headingEn;
		this.headingUr = headingUr;
		this.headingKn = headingKn;
		this.bodyEn = bodyEn;
		this.bodyUr = bodyUr;
		this.bodyKn = bodyKn;
		this.imageFilename = imageFilename;
		this.imageContentType = imageContentType;
		this.createdAt = LocalDateTime.now();
	}

	public Long getId() {
		return id;
	}

	public String getCategory() {
		return category;
	}

	public String getHeadingEn() {
		return headingEn;
	}

	public String getHeadingUr() {
		return headingUr;
	}

	public String getHeadingKn() {
		return headingKn;
	}

	public String getBodyEn() {
		return bodyEn;
	}

	public String getBodyUr() {
		return bodyUr;
	}

	public String getBodyKn() {
		return bodyKn;
	}

	public String getImageFilename() {
		return imageFilename;
	}

	public String getImageContentType() {
		return imageContentType;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public String heading(String language) {
		return switch (language) {
			case "ur" -> headingUr;
			case "kn" -> headingKn;
			default -> headingEn;
		};
	}

	public String body(String language) {
		return switch (language) {
			case "ur" -> bodyUr;
			case "kn" -> bodyKn;
			default -> bodyEn;
		};
	}
}
