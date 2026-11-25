package com.example.news.controller;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.transaction.annotation.Transactional;

import com.example.news.entity.NewsAnnotation;
import com.example.news.entity.PdfDocument;
import com.example.news.repository.NewsAnnotationRepository;
import com.example.news.repository.PdfDocumentRepository;
import com.example.news.service.PdfAutoDetectionService;
import com.example.news.service.PdfStorageService;
import org.apache.pdfbox.pdmodel.PDDocument;

@Controller
@Validated
public class AdminController {

	private static final Logger logger = LoggerFactory.getLogger(AdminController.class);
	private final PdfDocumentRepository documents;
	private final NewsAnnotationRepository annotations;
	private final PdfStorageService storage;
	private final PdfAutoDetectionService autoDetection;

	public AdminController(PdfDocumentRepository documents, NewsAnnotationRepository annotations,
			PdfStorageService storage, PdfAutoDetectionService autoDetection) {
		this.documents = documents;
		this.annotations = annotations;
		this.storage = storage;
		this.autoDetection = autoDetection;
	}

	@GetMapping("/login")
	public String login() {
		return "login";
	}

	@GetMapping("/admin/dashboard")
	public String dashboard(Model model) {
		model.addAttribute("documents", documents.findAllByOrderByUploadedAtDesc());
		return "admin/dashboard";
	}

	@PostMapping("/admin/pdf/upload")
	public String upload(@RequestParam("file") MultipartFile file, RedirectAttributes redirectAttributes)
			throws IOException {
		PdfStorageService.StoredPdf stored;
		try {
			stored = storage.store(file);
		} catch (IllegalArgumentException exception) {
			redirectAttributes.addFlashAttribute("error", exception.getMessage());
			return "redirect:/admin/dashboard";
		} catch (IOException exception) {
			logger.error("Could not store or parse uploaded PDF.", exception);
			redirectAttributes.addFlashAttribute("error", "The PDF could not be stored or read. Please try again.");
			return "redirect:/admin/dashboard";
		}
		PdfDocument document = new PdfDocument(stored.originalFilename(), stored.storedFilename(),
				LocalDateTime.now(), stored.pageCount());
		try {
			documents.save(document);
		} catch (RuntimeException exception) {
			java.nio.file.Files.deleteIfExists(storage.resolveStoredFilename(stored.storedFilename()));
			throw exception;
		}
		logger.info("Uploaded PDF '{}' ({} bytes) at {}", stored.originalFilename(), file.getSize(),
				document.getUploadedAt());
		redirectAttributes.addFlashAttribute("success", "PDF uploaded successfully.");
		return "redirect:/admin/annotate/" + document.getId();
	}

	@GetMapping("/admin/annotate/{pdfId}")
	public String annotate(@PathVariable Long pdfId, Model model) {
		model.addAttribute("document", findDocument(pdfId));
		return "admin/annotate";
	}

	@GetMapping("/admin/annotations/{pdfId}")
	@ResponseBody
	public List<AnnotationView> adminAnnotations(@PathVariable Long pdfId) {
		findDocument(pdfId);
		return annotations.findByPdfDocumentIdOrderByPageNumberAscIdAsc(pdfId).stream()
				.map(AnnotationView::from).toList();
	}

	@PostMapping("/admin/annotations/save")
	@ResponseBody
	@Transactional
	public ResponseEntity<Void> saveAnnotations(@Valid @RequestBody SaveAnnotationsRequest request) {
		PdfDocument document = findDocument(request.pdfId());
		if (request.annotations() == null || request.annotations().size() > 1000) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provide at most 1,000 annotations.");
		}

		List<NewsAnnotation> validated = request.annotations().stream().map(item -> {
			if (item.pageNumber() == null || item.pageNumber() < 1 || item.pageNumber() > document.getPageCount()
					|| !finitePositive(item.pdfWidth()) || !finitePositive(item.pdfHeight())
					|| !finiteCoordinate(item.pdfX()) || !finiteCoordinate(item.pdfY())
					|| item.newsTitle() == null || item.newsTitle().isBlank() || item.newsTitle().length() > 200) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "An annotation contains invalid values.");
			}
			return new NewsAnnotation(document.getId(), item.pageNumber(), item.pdfX(), item.pdfY(),
					item.pdfWidth(), item.pdfHeight(), item.newsTitle().strip());
		}).toList();

		annotations.deleteByPdfDocumentId(document.getId());
		annotations.saveAll(validated);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/admin/pdf/{pdfId}/publish")
	public String publish(@PathVariable Long pdfId, RedirectAttributes redirectAttributes) {
		PdfDocument document = findDocument(pdfId);
		document.setPublished(true);
		documents.save(document);
		redirectAttributes.addFlashAttribute("success", "PDF published for visitors.");
		return "redirect:/admin/dashboard";
	}

	@PostMapping("/admin/annotations/auto-detect/{pdfId}")
	@ResponseBody
	public ResponseEntity<List<PdfAutoDetectionService.Candidate>> autoDetect(@PathVariable Long pdfId)
			throws IOException {
		PdfDocument pdf = findDocument(pdfId);
		try (PDDocument source = PDDocument.load(storage.resolveStoredFilename(pdf.getStoredFilename()).toFile())) {
			return ResponseEntity.ok(autoDetection.detect(source));
		} catch (IOException exception) {
			logger.error("Could not detect article candidates in PDF {}.", pdfId, exception);
			throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
					"Could not read the PDF text. Scanned pages need OCR and cannot be auto-detected yet.", exception);
		}
	}

	private PdfDocument findDocument(Long pdfId) {
		return documents.findById(pdfId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "PDF not found."));
	}

	private boolean finitePositive(Float value) {
		return value != null && Float.isFinite(value) && value > 0;
	}

	private boolean finiteCoordinate(Float value) {
		return value != null && Float.isFinite(value);
	}

	public record AnnotationRequest(Integer pageNumber, Float pdfX, Float pdfY, Float pdfWidth, Float pdfHeight,
			@NotBlank @Size(max = 200) String newsTitle) {
	}

	public record SaveAnnotationsRequest(@NotNull Long pdfId,
			@NotNull List<@NotNull @Valid AnnotationRequest> annotations) {
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
