package com.example.news.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
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
}
