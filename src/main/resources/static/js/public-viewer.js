(() => {
	"use strict";

	const app = document.getElementById("reader-app");
	if (!app || !app.dataset.pdfId || !window.pdfjsLib) return;

	const pdfId = app.dataset.pdfId;
	const i18n = window.newsI18n || { text: key => key };
	const canvas = document.getElementById("reader-canvas");
	const overlay = document.getElementById("reader-overlay");
	const status = document.getElementById("reader-status");
	const context = canvas.getContext("2d");
	const modal = document.getElementById("story-modal");
	const titleElement = document.getElementById("story-title");
	const imageElement = document.getElementById("story-image");
	let pdf = null;
	let pageNumber = 1;
	let zoom = 1;
	let viewport = null;
	let annotations = [];

	window.pdfjsLib.GlobalWorkerOptions.workerSrc =
		"https://cdnjs.cloudflare.com/ajax/libs/pdf.js/3.11.174/pdf.worker.min.js";

	function setStatus(message, isError = false) {
		status.textContent = message;
		status.classList.toggle("error", isError);
	}

	function shareUrl(annotation) {
		const query = new URLSearchParams({ pdf: pdfId, lang: i18n.language || "en" });
		return `${window.location.origin}/?${query.toString()}#annotation-${annotation.id}`;
	}

	function openStory(annotation, rectangle) {
		const scaleX = canvas.width / canvas.clientWidth;
		const scaleY = canvas.height / canvas.clientHeight;
		const left = Math.max(0, rectangle.left);
		const top = Math.max(0, rectangle.top);
		const width = Math.min(rectangle.width, canvas.clientWidth - left);
		const height = Math.min(rectangle.height, canvas.clientHeight - top);
		if (width <= 0 || height <= 0) return;

		const crop = document.createElement("canvas");
		crop.width = Math.max(1, Math.round(width * scaleX));
		crop.height = Math.max(1, Math.round(height * scaleY));
		crop.getContext("2d").drawImage(
			canvas,
			left * scaleX, top * scaleY, width * scaleX, height * scaleY,
			0, 0, crop.width, crop.height
		);
		imageElement.src = crop.toDataURL("image/png");
		titleElement.textContent = annotation.newsTitle;
		const url = shareUrl(annotation);
		document.getElementById("share-facebook").href =
			`https://www.facebook.com/sharer/sharer.php?u=${encodeURIComponent(url)}`;
		document.getElementById("share-twitter").href =
			`https://twitter.com/intent/tweet?url=${encodeURIComponent(url)}&text=${encodeURIComponent(annotation.newsTitle)}`;
		document.getElementById("share-whatsapp").href =
			`https://wa.me/?text=${encodeURIComponent(`${annotation.newsTitle} ${url}`)}`;
		document.getElementById("copy-link").dataset.shareUrl = url;
		document.getElementById("share-status").textContent = "";
		modal.hidden = false;
		document.getElementById("close-modal").focus();
	}

	function renderOverlays() {
		overlay.replaceChildren();
		for (const annotation of annotations.filter(item => item.pageNumber === pageNumber)) {
			const [x1, y1, x2, y2] = viewport.convertToViewportRectangle([
				annotation.pdfX, annotation.pdfY,
				annotation.pdfX + annotation.pdfWidth, annotation.pdfY + annotation.pdfHeight
			]);
			const left = Math.min(x1, x2);
			const top = Math.min(y1, y2);
			const width = Math.abs(x2 - x1);
			const height = Math.abs(y2 - y1);
			const element = document.createElement("button");
			element.type = "button";
			element.className = "article-overlay";
			element.dataset.annotationId = String(annotation.id);
			element.style.left = `${left}px`;
			element.style.top = `${top}px`;
			element.style.width = `${width}px`;
			element.style.height = `${height}px`;
			element.setAttribute("aria-label", i18n.text("readArticle", annotation.newsTitle));
			element.title = annotation.newsTitle;
			element.addEventListener("click", () => openStory(annotation, { left, top, width, height }));
			overlay.append(element);
		}
	}

	async function renderPage() {
		const page = await pdf.getPage(pageNumber);
		viewport = page.getViewport({ scale: zoom });
		const outputScale = Math.max(1, window.devicePixelRatio || 1);
		const renderViewport = page.getViewport({ scale: zoom * outputScale });
		canvas.width = Math.ceil(renderViewport.width);
		canvas.height = Math.ceil(renderViewport.height);
		canvas.style.width = `${viewport.width}px`;
		canvas.style.height = `${viewport.height}px`;
		overlay.style.width = `${viewport.width}px`;
		overlay.style.height = `${viewport.height}px`;
		await page.render({ canvasContext: context, viewport: renderViewport }).promise;
		document.getElementById("reader-page-indicator").textContent = i18n.text("page", pageNumber, pdf.numPages);
		document.getElementById("reader-previous").disabled = pageNumber <= 1;
		document.getElementById("reader-next").disabled = pageNumber >= pdf.numPages;
		document.getElementById("reader-zoom-indicator").textContent = `${Math.round(zoom * 100)}%`;
		renderOverlays();
	}

	document.getElementById("reader-previous").addEventListener("click", async () => {
		if (pageNumber > 1) { pageNumber--; await renderPage(); }
	});
	document.getElementById("reader-next").addEventListener("click", async () => {
		if (pdf && pageNumber < pdf.numPages) { pageNumber++; await renderPage(); }
	});
	document.getElementById("reader-zoom-in").addEventListener("click", async () => {
		if (zoom < 4) { zoom = Math.min(4, zoom + 0.25); await renderPage(); }
	});
	document.getElementById("reader-zoom-out").addEventListener("click", async () => {
		if (zoom > 0.5) { zoom = Math.max(0.5, zoom - 0.25); await renderPage(); }
	});
	document.getElementById("close-modal").addEventListener("click", () => { modal.hidden = true; });
	modal.addEventListener("click", event => {
		if (event.target === modal) modal.hidden = true;
	});
	document.addEventListener("keydown", event => {
		if (event.key === "Escape" && !modal.hidden) modal.hidden = true;
	});
	document.getElementById("copy-link").addEventListener("click", async event => {
		try {
			await navigator.clipboard.writeText(event.currentTarget.dataset.shareUrl);
			document.getElementById("share-status").textContent = i18n.text("linkCopied");
		} catch {
			document.getElementById("share-status").textContent = i18n.text("copyFailed");
		}
	});

	(async () => {
		try {
			const annotationResponse = await fetch(`/news/annotations/${encodeURIComponent(pdfId)}`);
			if (!annotationResponse.ok) throw new Error(`${i18n.text("loadArticleError")} (${annotationResponse.status}).`);
			annotations = await annotationResponse.json();
			const task = window.pdfjsLib.getDocument(`/pdf/view/${encodeURIComponent(pdfId)}`);
			pdf = await task.promise;
			const fragmentMatch = window.location.hash.match(/^#annotation-(\d+)$/);
			const linkedAnnotation = fragmentMatch
				? annotations.find(annotation => annotation.id === Number(fragmentMatch[1]))
				: null;
			if (linkedAnnotation) pageNumber = linkedAnnotation.pageNumber;
			await renderPage();
			setStatus(annotations.length ? i18n.text("selectStory") : i18n.text("noArticles"));
			if (linkedAnnotation) {
				const button = overlay.querySelector(`[data-annotation-id="${linkedAnnotation.id}"]`);
				button?.click();
			}
		} catch (error) {
			setStatus(error.message || i18n.text("loadPdfError"), true);
		}
	})();
})();
