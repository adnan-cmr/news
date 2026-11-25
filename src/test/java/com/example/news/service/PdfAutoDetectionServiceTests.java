package com.example.news.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.junit.jupiter.api.Test;

class PdfAutoDetectionServiceTests {

	private final PdfAutoDetectionService service = new PdfAutoDetectionService();

	@Test
	void detectsTextBlocksAndReturnsPdfCoordinates() throws IOException {
		try (PDDocument document = new PDDocument()) {
			PDPage page = new PDPage(PDRectangle.LETTER);
			document.addPage(page);
			try (PDPageContentStream content = new PDPageContentStream(document, page)) {
				content.beginText();
				content.setFont(PDType1Font.HELVETICA, 12);
				content.setLeading(16);
				content.newLineAtOffset(75, 700);
				content.showText("Local council approves a new public library");
				content.newLine();
				content.showText("The approved plan includes community reading rooms");
				content.newLine();
				content.showText("Construction is expected to begin later this year");
				content.endText();
			}

			List<PdfAutoDetectionService.Candidate> candidates = service.detect(document);

			assertThat(candidates).hasSize(1);
			PdfAutoDetectionService.Candidate candidate = candidates.get(0);
			assertThat(candidate.pageNumber()).isEqualTo(1);
			assertThat(candidate.suggestedTitle()).startsWith("Local council approves");
			assertThat(candidate.pdfX()).isGreaterThanOrEqualTo(75);
			assertThat(candidate.pdfY()).isGreaterThan(0);
			assertThat(candidate.pdfWidth()).isGreaterThan(200);
			assertThat(candidate.pdfHeight()).isGreaterThan(28);
		}
	}

	@Test
	void returnsNoCandidatesForAVisualOnlyPage() throws IOException {
		try (PDDocument document = new PDDocument()) {
			document.addPage(new PDPage(PDRectangle.LETTER));

			assertThat(service.detect(document)).isEmpty();
		}
	}

	@Test
	void keepsArticlesInDifferentColumnsSeparate() throws IOException {
		try (PDDocument document = new PDDocument()) {
			PDPage page = new PDPage(PDRectangle.LETTER);
			document.addPage(page);
			try (PDPageContentStream content = new PDPageContentStream(document, page)) {
				writeArticle(content, 50, 700, "Mayor opens new bridge");
				writeArticle(content, 330, 700, "Schools reopen after repairs");
			}

			List<PdfAutoDetectionService.Candidate> candidates = service.detect(document);

			assertThat(candidates).hasSize(2);
			assertThat(candidates.get(0).suggestedTitle()).startsWith("Mayor");
			assertThat(candidates.get(1).suggestedTitle()).startsWith("Schools");
			assertThat(candidates.get(0).pdfX() + candidates.get(0).pdfWidth()).isLessThan(300);
			assertThat(candidates.get(1).pdfX()).isGreaterThan(300);
		}
	}

	@Test
	void doesNotLetArticleBoxesCrossAPhoto() throws IOException {
		try (PDDocument document = new PDDocument()) {
			PDPage page = new PDPage(PDRectangle.LETTER);
			document.addPage(page);
			PDImageXObject photo = LosslessFactory.createFromImage(document,
					new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB));
			try (PDPageContentStream content = new PDPageContentStream(document, page)) {
				writeArticle(content, 50, 700, "Morning market opens");
				content.drawImage(photo, 50, 560, 200, 60);
				writeArticle(content, 50, 530, "Evening concert announced");
			}

			List<PdfAutoDetectionService.Candidate> candidates = service.detect(document);

			assertThat(candidates).hasSize(2);
			for (PdfAutoDetectionService.Candidate candidate : candidates) {
				boolean abovePhoto = candidate.pdfY() >= 620;
				boolean belowPhoto = candidate.pdfY() + candidate.pdfHeight() <= 560;
				assertThat(abovePhoto || belowPhoto).isTrue();
			}
		}
	}

	private static void writeArticle(PDPageContentStream content, float x, float y, String headline)
			throws IOException {
		content.beginText();
		content.setFont(PDType1Font.HELVETICA_BOLD, 16);
		content.newLineAtOffset(x, y);
		content.showText(headline);
		content.setFont(PDType1Font.HELVETICA, 10);
		content.setLeading(14);
		content.newLine();
		content.showText("Residents crossed the river on foot today.");
		content.newLine();
		content.showText("Officials said the work finished early.");
		content.endText();
	}
}
