package com.example.news.controller;

import java.io.IOException;
import java.util.List;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.news.entity.NewsPost;
import com.example.news.repository.NewsPostRepository;
import com.example.news.service.ImageStorageService;

@Controller
public class NewsPostController {

	public static final List<String> CATEGORIES = List.of("World", "National", "Politics", "Business", "Sports",
			"Entertainment", "Technology", "Health", "Education", "Local");

	private final NewsPostRepository posts;
	private final ImageStorageService images;

	public NewsPostController(NewsPostRepository posts, ImageStorageService images) {
		this.posts = posts;
		this.images = images;
	}

	@GetMapping("/admin/posts")
	public String list(Model model) {
		model.addAttribute("posts", posts.findAllByOrderByCreatedAtDesc());
		model.addAttribute("categories", CATEGORIES);
		return "admin/posts";
	}

	@PostMapping("/admin/posts")
	public String create(@RequestParam String category, @RequestParam String headingEn,
			@RequestParam String headingUr, @RequestParam String headingKn, @RequestParam String bodyEn,
			@RequestParam String bodyUr, @RequestParam String bodyKn,
			@RequestParam("image") MultipartFile image, RedirectAttributes redirectAttributes)
			throws IOException {
		String error = validate(category, headingEn, headingUr, headingKn, bodyEn, bodyUr, bodyKn);
		if (error != null) {
			redirectAttributes.addFlashAttribute("error", error);
			return "redirect:/admin/posts";
		}
		ImageStorageService.StoredImage stored;
		try {
			stored = images.store(image);
		} catch (IllegalArgumentException exception) {
			redirectAttributes.addFlashAttribute("error", exception.getMessage());
			return "redirect:/admin/posts";
		}
		try {
			posts.save(new NewsPost(category, headingEn.strip(), headingUr.strip(), headingKn.strip(),
					bodyEn.strip(), bodyUr.strip(), bodyKn.strip(), stored.filename(), stored.contentType()));
		} catch (RuntimeException exception) {
			images.delete(stored.filename());
			throw exception;
		}
		redirectAttributes.addFlashAttribute("success", "News posted in all languages.");
		return "redirect:/admin/posts";
	}

	@PostMapping("/admin/posts/{id}/delete")
	public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) throws IOException {
		NewsPost post = find(id);
		posts.delete(post);
		images.delete(post.getImageFilename());
		redirectAttributes.addFlashAttribute("success", "News deleted.");
		return "redirect:/admin/posts";
	}

	@GetMapping("/news/post/{id}/image")
	public ResponseEntity<Resource> image(@PathVariable Long id) {
		NewsPost post = find(id);
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType(post.getImageContentType()))
				.cacheControl(CacheControl.noCache())
				.header("X-Content-Type-Options", "nosniff")
				.body(new FileSystemResource(images.resolve(post.getImageFilename())));
	}

	private NewsPost find(Long id) {
		return posts.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "News not found."));
	}

	private String validate(String category, String... texts) {
		if (!CATEGORIES.contains(category)) {
			return "Select a valid category.";
		}
		String[] names = { "English heading", "Urdu heading", "Kannada heading", "English body", "Urdu body",
				"Kannada body" };
		for (int i = 0; i < texts.length; i++) {
			if (texts[i] == null || texts[i].isBlank()) {
				return names[i] + " is required.";
			}
			if (i < 3 && texts[i].strip().length() > 200) {
				return names[i] + " must be at most 200 characters.";
			}
			if (i >= 3 && texts[i].length() > 20000) {
				return names[i] + " must be at most 20,000 characters.";
			}
		}
		return null;
	}
}
