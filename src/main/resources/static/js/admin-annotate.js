(() => {
	"use strict";

	const app = document.getElementById("annotation-app");
	if (!app || !window.pdfjsLib) return;

	const pdfId = app.dataset.pdfId;
	const canvas = document.getElementById("pdf-canvas");
	const overlay = document.getElementById("drawing-overlay");
	const status = document.getElementById("editor-status");
	const list = document.getElementById("annotation-list");
	const emptyList = document.getElementById("empty-annotations");
	const pageIndicator = document.getElementById("page-indicator");
	const context = canvas.getContext("2d");
	const csrfToken = document.querySelector('meta[name="csrf-token"]').content;
	const csrfHeader = document.querySelector('meta[name="csrf-header"]').content;
	let pdf = null;
	let pageNumber = 1;
	let viewport = null;
	let zoom = 2;
	let allAnnotations = [];
	let dragStart = null;
	let preview = null;
	let redrawAnnotation = null;

	window.pdfjsLib.GlobalWorkerOptions.workerSrc =
		"https://cdnjs.cloudflare.com/ajax/libs/pdf.js/3.11.174/pdf.worker.min.js";

	function setStatus(message, isError = false) {
		status.textContent = message;
		status.classList.toggle("error", isError);
		status.classList.toggle("success", !isError && message.includes("saved"));
	}

	function renderAnnotationList() {
		list.replaceChildren();
		const current = allAnnotations.filter(item => item.pageNumber === pageNumber);
		emptyList.hidden = current.length > 0;
		for (const annotation of current) {
			const item = document.createElement("li");
			item.className = "annotation-item";
			const title = document.createElement("span");
			title.className = "annotation-title";
			title.textContent = annotation.newsTitle;
			const remove = document.createElement("button");
			remove.className = "delete-annotation";
			remove.type = "button";
			remove.textContent = "Delete";
			remove.setAttribute("aria-label", `Delete ${annotation.newsTitle}`);
			const redraw = document.createElement("button");
			redraw.className = "delete-annotation adjust-annotation";
			redraw.type = "button";
			redraw.textContent = "Redraw";
			redraw.setAttribute("aria-label", `Redraw box for ${annotation.newsTitle}`);
			redraw.addEventListener("click", async () => {
				redrawAnnotation = annotation;
				if (annotation.pageNumber !== pageNumber) {
					pageNumber = annotation.pageNumber;
					await renderPage();
				} else {
					renderExistingRectangles();
				}
				overlay.querySelector(".is-redraw-target")?.scrollIntoView({ block: "center", inline: "center", behavior: "smooth" });
				setStatus(`Draw a replacement box for “${annotation.newsTitle}”. Its highlighted box will be replaced.`);
			});
			const editTitle = document.createElement("button");
			editTitle.className = "delete-annotation adjust-annotation";
			editTitle.type = "button";
			editTitle.textContent = "Rename";
			editTitle.setAttribute("aria-label", `Rename ${annotation.newsTitle}`);
			editTitle.addEventListener("click", () => {
				const title = window.prompt("Article title:", annotation.newsTitle);
				if (title && title.trim()) {
					annotation.newsTitle = title.trim().slice(0, 200);
					renderAnnotationList();
					renderExistingRectangles();
				}
			});
			remove.addEventListener("click", () => {
				if (redrawAnnotation === annotation) redrawAnnotation = null;
				allAnnotations = allAnnotations.filter(candidate => candidate !== annotation);
				renderAnnotationList();
				renderPage();
			});
			item.append(title, redraw, editTitle, remove);
			list.append(item);
		}
	}

	function renderExistingRectangles() {
		overlay.querySelectorAll(".drawn-rectangle").forEach(element => element.remove());
		for (const item of allAnnotations.filter(annotation => annotation.pageNumber === pageNumber)) {
			const [x1, y1, x2, y2] = viewport.convertToViewportRectangle([
				item.pdfX, item.pdfY, item.pdfX + item.pdfWidth, item.pdfY + item.pdfHeight
			]);
			const rectangle = document.createElement("div");
			rectangle.className = "drawn-rectangle";
			rectangle.title = item.newsTitle;
			if (item === redrawAnnotation) rectangle.classList.add("is-redraw-target");
			rectangle.style.left = `${Math.min(x1, x2)}px`;
			rectangle.style.top = `${Math.min(y1, y2)}px`;
			rectangle.style.width = `${Math.abs(x2 - x1)}px`;
			rectangle.style.height = `${Math.abs(y2 - y1)}px`;
			overlay.append(rectangle);
		}
	}

	async function renderPage() {
		if (!pdf) return;
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
		pageIndicator.textContent = `Page ${pageNumber} of ${pdf.numPages}`;
		document.getElementById("previous-page").disabled = pageNumber <= 1;
		document.getElementById("next-page").disabled = pageNumber >= pdf.numPages;
		document.getElementById("zoom-indicator").textContent = `${Math.round(zoom * 100)}%`;
		renderExistingRectangles();
		renderAnnotationList();
	}

	function getOcrLines(data, rtl) {
		const lines = [];
		for (const block of data.blocks || []) {
			for (const paragraph of block.paragraphs || []) {
				for (const line of paragraph.lines || []) {
					const box = line.bbox;
					const text = (line.text || "").trim();
					if (!box || text.length < 3 || (line.confidence ?? 100) < 20) continue;
					lines.push({
						left: box.x0,
						top: box.y0,
						right: box.x1,
						bottom: box.y1,
						text,
						columnEdge: rtl ? box.x1 : box.x0
					});
				}
			}
		}
		return lines;
	}

	function createOcrAnnotations(lines, detectedPageNumber, ocrViewport) {
		const columns = [];
		for (const line of lines.sort((a, b) => a.columnEdge - b.columnEdge)) {
			const limit = Math.max(65, ocrViewport.scale * 45);
			let column = columns.find(item => Math.abs(item.edge - line.columnEdge) <= limit);
			if (!column) {
				column = { edge: line.columnEdge, lines: [] };
				columns.push(column);
			}
			column.lines.push(line);
			column.edge = column.lines.reduce((sum, item) => sum + item.columnEdge, 0) / column.lines.length;
		}

		const annotations = [];
		for (const column of columns) {
			const orderedLines = column.lines.sort((a, b) => a.top - b.top);
			let group = [];
			const saveGroup = () => {
				if (group.length < 3) {
					group = [];
					return;
				}
				const left = Math.max(0, Math.min(...group.map(line => line.left)) - 6);
				const top = Math.max(0, Math.min(...group.map(line => line.top)) - 6);
				const right = Math.min(ocrViewport.width, Math.max(...group.map(line => line.right)) + 6);
				const bottom = Math.min(ocrViewport.height, Math.max(...group.map(line => line.bottom)) + 6);
				if (right - left < 80 || bottom - top < 40) {
					group = [];
					return;
				}
				const first = ocrViewport.convertToPdfPoint(left, top);
				const second = ocrViewport.convertToPdfPoint(right, bottom);
				annotations.push({
					pageNumber: detectedPageNumber,
					pdfX: Math.min(first[0], second[0]),
					pdfY: Math.min(first[1], second[1]),
					pdfWidth: Math.abs(second[0] - first[0]),
					pdfHeight: Math.abs(second[1] - first[1]),
					newsTitle: group[0].text.slice(0, 200) || "OCR article candidate"
				});
				group = [];
			};

			for (const line of orderedLines) {
				const previous = group[group.length - 1];
				const gap = previous ? line.top - previous.bottom : 0;
				const maxGap = Math.max(36, ocrViewport.scale * 18);
				if (previous && gap > maxGap) saveGroup();
				group.push(line);
			}
			saveGroup();
		}
		return annotations;
	}

	async function detectScannedPages(existingPageNumbers, language) {
		const pagesToScan = [];
		for (let number = 1; number <= pdf.numPages; number++) {
			if (!existingPageNumbers.has(number)) pagesToScan.push(number);
		}
		if (!pagesToScan.length) return [];
		if (!window.Tesseract) throw new Error("OCR could not load. Check the connection and try again.");

		let activePageNumber = 0;
		setStatus(`Loading ${language} OCR model for ${pagesToScan.length} scanned page(s)…`);
		const worker = await window.Tesseract.createWorker(language, 1, {
			workerPath: "https://cdn.jsdelivr.net/npm/tesseract.js@5/dist/worker.min.js",
			corePath: "https://cdn.jsdelivr.net/npm/tesseract.js-core@5/",
			langPath: "https://tessdata.projectnaptha.com/4.0.0_best",
			gzip: true,
			logger: message => {
				if (message.status === "recognizing text" && Number.isFinite(message.progress)) {
					setStatus(`Reading scanned page ${activePageNumber} of ${pdf.numPages} (${Math.round(message.progress * 100)}%)…`);
				}
			}
		});
		const annotations = [];
		try {
			await worker.setParameters({ tessedit_pageseg_mode: "3" });
			for (let index = 0; index < pagesToScan.length; index++) {
				const detectedPageNumber = pagesToScan[index];
				activePageNumber = detectedPageNumber;
				setStatus(`Running OCR on scanned page ${detectedPageNumber} of ${pdf.numPages} (${index + 1}/${pagesToScan.length})…`);
				const page = await pdf.getPage(detectedPageNumber);
				const ocrViewport = page.getViewport({ scale: 2 });
				const ocrCanvas = document.createElement("canvas");
				ocrCanvas.width = Math.ceil(ocrViewport.width);
				ocrCanvas.height = Math.ceil(ocrViewport.height);
				const ocrContext = ocrCanvas.getContext("2d");
				if (!ocrContext) throw new Error("Could not create a canvas for scanned-page OCR.");
				let data;
				try {
					await page.render({ canvasContext: ocrContext, viewport: ocrViewport }).promise;
					({ data } = await worker.recognize(ocrCanvas, {}, { blocks: true }));
				} finally {
					ocrCanvas.width = 0;
					ocrCanvas.height = 0;
				}
				const lines = getOcrLines(data, language === "urd");
				annotations.push(...createOcrAnnotations(lines, detectedPageNumber, ocrViewport));
			}
		} finally {
			await worker.terminate();
		}
		return annotations;
	}

	overlay.addEventListener("pointerdown", event => {
		if (!viewport) return;
		const bounds = overlay.getBoundingClientRect();
		dragStart = { x: event.clientX - bounds.left, y: event.clientY - bounds.top };
		preview = document.createElement("div");
		preview.className = "drawn-rectangle";
		overlay.append(preview);
		overlay.setPointerCapture(event.pointerId);
	});

	overlay.addEventListener("pointermove", event => {
		if (!dragStart || !preview) return;
		const bounds = overlay.getBoundingClientRect();
		const x = event.clientX - bounds.left;
		const y = event.clientY - bounds.top;
		preview.style.left = `${Math.min(x, dragStart.x)}px`;
		preview.style.top = `${Math.min(y, dragStart.y)}px`;
		preview.style.width = `${Math.abs(x - dragStart.x)}px`;
		preview.style.height = `${Math.abs(y - dragStart.y)}px`;
	});

	overlay.addEventListener("pointerup", event => {
		if (!dragStart || !preview) return;
		const bounds = overlay.getBoundingClientRect();
		const end = {
			x: Math.max(0, Math.min(overlay.clientWidth, event.clientX - bounds.left)),
			y: Math.max(0, Math.min(overlay.clientHeight, event.clientY - bounds.top))
		};
		const start = dragStart;
		const drawn = preview;
		dragStart = null;
		preview = null;
		drawn.remove();
		if (Math.abs(end.x - start.x) * Math.abs(end.y - start.y) <= 100) return;
		const first = viewport.convertToPdfPoint(start.x, start.y);
		const second = viewport.convertToPdfPoint(end.x, end.y);
		if (redrawAnnotation) {
			redrawAnnotation.pdfX = Math.min(first[0], second[0]);
			redrawAnnotation.pdfY = Math.min(first[1], second[1]);
			redrawAnnotation.pdfWidth = Math.abs(second[0] - first[0]);
			redrawAnnotation.pdfHeight = Math.abs(second[1] - first[1]);
			redrawAnnotation = null;
			renderAnnotationList();
			renderExistingRectangles();
			setStatus("Box updated. Save all annotations to keep your changes.");
			return;
		}
		const title = window.prompt("Title for this news article:");
		if (!title || !title.trim()) return;
		allAnnotations.push({
			pageNumber,
			pdfX: Math.min(first[0], second[0]),
			pdfY: Math.min(first[1], second[1]),
			pdfWidth: Math.abs(second[0] - first[0]),
			pdfHeight: Math.abs(second[1] - first[1]),
			newsTitle: title.trim()
		});
		renderAnnotationList();
		renderExistingRectangles();
		setStatus("Unsaved annotation added.");
	});
	overlay.addEventListener("pointercancel", () => {
		dragStart = null;
		preview?.remove();
		preview = null;
	});

	document.getElementById("previous-page").addEventListener("click", async () => {
		if (pageNumber > 1) {
			if (redrawAnnotation) {
				redrawAnnotation = null;
				setStatus("Box redraw cancelled.");
			}
			pageNumber--;
			await renderPage();
		}
	});
	document.getElementById("next-page").addEventListener("click", async () => {
		if (pdf && pageNumber < pdf.numPages) {
			if (redrawAnnotation) {
				redrawAnnotation = null;
				setStatus("Box redraw cancelled.");
			}
			pageNumber++;
			await renderPage();
		}
	});
	document.getElementById("zoom-in").addEventListener("click", async () => {
		if (zoom < 4) { zoom = Math.min(4, zoom + 0.25); await renderPage(); }
	});
	document.getElementById("zoom-out").addEventListener("click", async () => {
		if (zoom > 0.5) { zoom = Math.max(0.5, zoom - 0.25); await renderPage(); }
	});
	document.getElementById("auto-detect").addEventListener("click", async event => {
		const button = event.currentTarget;
		button.disabled = true;
		setStatus("Finding article text blocks…");
		try {
			const response = await fetch(`/admin/annotations/auto-detect/${encodeURIComponent(pdfId)}`, {
				method: "POST",
				headers: { [csrfHeader]: csrfToken }
			});
			if (!response.ok) {
				const body = await response.json().catch(() => ({}));
				throw new Error(body.detail || `Auto-detect failed (${response.status}).`);
			}
			const candidates = await response.json();
			const candidateAnnotations = candidates.map(candidate => ({
				pageNumber: candidate.pageNumber,
				pdfX: candidate.pdfX,
				pdfY: candidate.pdfY,
				pdfWidth: candidate.pdfWidth,
				pdfHeight: candidate.pdfHeight,
				newsTitle: candidate.suggestedTitle || "Article candidate"
			}));
			const detectedPages = new Set(candidateAnnotations.map(candidate => candidate.pageNumber));
			const language = document.getElementById("ocr-language").value;
			const ocrAnnotations = await detectScannedPages(detectedPages, language);
			const newAnnotations = [...candidateAnnotations, ...ocrAnnotations]
				.sort((left, right) => left.pageNumber - right.pageNumber || left.pdfY - right.pdfY);
			allAnnotations.push(...newAnnotations);
			if (newAnnotations.length) {
				pageNumber = newAnnotations[0].pageNumber;
			}
			await renderPage();
			if (newAnnotations.length) {
				setStatus(`Found ${newAnnotations.length} candidate boxes across ${new Set(newAnnotations.map(item => item.pageNumber)).size} page(s). Review and adjust them, then save.`);
			} else {
				setStatus("No article boxes were detected. Try another OCR language or draw boxes manually.", true);
			}
		} catch (error) {
			setStatus(error.message || "Unable to detect article boxes.", true);
		} finally {
			button.disabled = false;
		}
	});
	document.getElementById("save-annotations").addEventListener("click", async event => {
		const button = event.currentTarget;
		button.disabled = true;
		setStatus("Saving annotations…");
		try {
			const response = await fetch("/admin/annotations/save", {
				method: "POST",
				headers: { "Content-Type": "application/json", [csrfHeader]: csrfToken },
				body: JSON.stringify({ pdfId: Number(pdfId), annotations: allAnnotations })
			});
			if (!response.ok) throw new Error(`Save failed (${response.status}).`);
			setStatus("All annotations saved.");
			const refreshed = await fetch(`/admin/annotations/${encodeURIComponent(pdfId)}`);
			if (!refreshed.ok) throw new Error(`Could not reload saved annotations (${refreshed.status}).`);
			allAnnotations = await refreshed.json();
			await renderPage();
		} catch (error) {
			setStatus(error.message || "Unable to save annotations.", true);
		} finally {
			button.disabled = false;
		}
	});

	(async () => {
		try {
			const response = await fetch(`/admin/annotations/${encodeURIComponent(pdfId)}`);
			if (!response.ok) throw new Error(`Could not load annotations (${response.status}).`);
			allAnnotations = await response.json();
			const task = window.pdfjsLib.getDocument(`/pdf/view/${encodeURIComponent(pdfId)}`);
			pdf = await task.promise;
			await renderPage();
			setStatus("Drag on the page to mark an article.");
		} catch (error) {
			setStatus(error.message || "Unable to load the PDF.", true);
		}
	})();
})();
