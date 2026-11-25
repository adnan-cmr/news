package com.example.news.controller;

import java.util.List;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

import com.example.news.entity.NewsAnnotation;
import com.example.news.entity.PdfDocument;
import com.example.news.repository.NewsAnnotationRepository;
import com.example.news.repository.NewsPostRepository;
import com.example.news.repository.PdfDocumentRepository;

@Controller
public class PublicController {

	private final PdfDocumentRepository documents;
	private final NewsAnnotationRepository annotations;

	private final NewsPostRepository newsPosts;

	public PublicController(PdfDocumentRepository documents, NewsAnnotationRepository annotations,
			NewsPostRepository newsPosts) {
		this.newsPosts = newsPosts;
		this.documents = documents;
		this.annotations = annotations;
	}

	@GetMapping("/")
	public String home(@RequestParam(required = false) Long pdf,
			@RequestParam(defaultValue = "en") String lang, Model model) {
		String language = lang.toLowerCase(java.util.Locale.ROOT);
		if (!Set.of("en", "ur", "kn").contains(language)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported language.");
		}
		PdfDocument selected;
		if (pdf != null) {
			selected = documents.findById(pdf)
					.filter(document -> Boolean.TRUE.equals(document.getPublished()))
					.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Published PDF not found."));
		} else {
			selected = documents.findFirstByPublishedTrueOrderByUploadedAtDesc().orElse(null);
		}
		model.addAttribute("document", selected);
		model.addAttribute("publishedDocuments", documents.findByPublishedTrueOrderByUploadedAtDesc());
		model.addAttribute("newsPosts", newsPosts.findAllByOrderByCreatedAtDesc());
		model.addAttribute("language", language);
		return "public/viewer";
	}

	@GetMapping("/news/{pdfId}")
	public String viewNews(@PathVariable Long pdfId) {
		return "redirect:/?pdf=" + pdfId;
	}

	@GetMapping("/news/annotations/{pdfId}")
	@ResponseBody
	public List<AnnotationView> publicAnnotations(@PathVariable Long pdfId) {
		PdfDocument document = documents.findById(pdfId)
				.filter(item -> Boolean.TRUE.equals(item.getPublished()))
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Published PDF not found."));
		return annotations.findByPdfDocumentIdOrderByPageNumberAscIdAsc(document.getId()).stream()
				.map(AnnotationView::from).toList();
	}

	public record AnnotationView(Long id, Integer pageNumber, Float pdfX, Float pdfY, Float pdfWidth,
			Float pdfHeight, String newsTitle) {
		static AnnotationView from(NewsAnnotation annotation) {
			return new AnnotationView(annotation.getId(), annotation.getPageNumber(), annotation.getPdfX(),
					annotation.getPdfY(), annotation.getPdfWidth(), annotation.getPdfHeight(),
					annotation.getNewsTitle());
		}
	}
}
