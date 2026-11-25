(() => {
	"use strict";

	const app = document.getElementById("reader-app");
	if (!app) return;

	const messages = {
		en: {
			brand: "The Newsroom", admin: "Admin", previousPapers: "Previous papers", language: "Language", editionByLanguage: "A global view, in your language", newsEyebrow: "LATEST NEWS",
			latestNews: "Latest news", sourceLanguageNote: "Stories are shown in their original language from free RSS sources.",
			refresh: "Refresh", loadingNews: "Loading news…", noNews: "No news has been posted yet.",
			newsError: "News feeds are temporarily unavailable.", readStory: "Read story", latestEdition: "LATEST EDITION",
			editionHint: "Choose a highlighted story to view and share its clipping.", previous: "Previous", next: "Next",
			loadingEdition: "Loading edition…", noEdition: "No newspaper has been published yet.",
			checkBack: "Please check back soon for the latest edition.", fromEdition: "FROM THIS EDITION",
			facebook: "Facebook", twitter: "X / Twitter", whatsapp: "WhatsApp", copyLink: "Copy link",
			linkCopied: "Link copied.", copyFailed: "Could not copy the link. Copy it from your browser address bar.",
			selectStory: "Select a highlighted article to read and share.",
			noArticles: "This edition has no linked articles yet.", loadArticleError: "Could not load article links.",
			loadPdfError: "Unable to load the newspaper.", page: (number, total) => `Page ${number} of ${total}`,
			zoomOut: "Zoom out", zoomIn: "Zoom in", readArticle: title => `Read article: ${title}`,
			publishedEditions: "Published editions", closeArticle: "Close article", croppedArticle: "Cropped newspaper article"
		},
		ur: {
			brand: "نیوز روم", admin: "منتظم", previousPapers: "پچھلے اخبارات", language: "زبان", editionByLanguage: "دنیا بھر کی خبریں، آپ کی زبان میں", newsEyebrow: "تازہ خبریں",
			latestNews: "تازہ ترین خبریں", sourceLanguageNote: "خبریں مفت آر ایس ایس ذرائع سے ان کی اصل زبان میں دکھائی جاتی ہیں۔",
			refresh: "تازہ کریں", loadingNews: "خبریں لوڈ ہو رہی ہیں…", noNews: "ابھی کوئی خبر پوسٹ نہیں کی گئی۔",
			newsError: "خبریں عارضی طور پر دستیاب نہیں ہیں۔", readStory: "خبر پڑھیں", latestEdition: "تازہ شمارہ",
			editionHint: "خبر پڑھنے اور شیئر کرنے کے لیے نمایاں حصے پر کلک کریں۔", previous: "پچھلا", next: "اگلا",
			loadingEdition: "شمارہ لوڈ ہو رہا ہے…", noEdition: "ابھی کوئی اخبار شائع نہیں ہوا۔",
			checkBack: "تازہ شمارے کے لیے بعد میں دوبارہ آئیں۔", fromEdition: "اس شمارے سے",
			facebook: "فیس بک", twitter: "ایکس / ٹوئٹر", whatsapp: "واٹس ایپ", copyLink: "لنک کاپی کریں",
			linkCopied: "لنک کاپی ہو گیا۔", copyFailed: "لنک کاپی نہیں ہو سکا۔ اسے براؤزر کے ایڈریس بار سے کاپی کریں۔",
			selectStory: "پڑھنے اور شیئر کرنے کے لیے نمایاں خبر منتخب کریں۔",
			noArticles: "اس شمارے میں خبر کے لنکس موجود نہیں ہیں۔", loadArticleError: "خبر کے لنکس لوڈ نہیں ہو سکے۔",
			loadPdfError: "اخبار لوڈ نہیں ہو سکا۔", page: (number, total) => `صفحہ ${number} از ${total}`,
			zoomOut: "زوم کم کریں", zoomIn: "زوم بڑھائیں", readArticle: title => `خبر پڑھیں: ${title}`,
			publishedEditions: "شائع شدہ شمارے", closeArticle: "خبر بند کریں", croppedArticle: "اخبار سے تراشی گئی خبر"
		},
		kn: {
			brand: "ನ್ಯೂಸ್‌ರೂಮ್", admin: "ನಿರ್ವಾಹಕರು", previousPapers: "ಹಿಂದಿನ ಪತ್ರಿಕೆಗಳು", language: "ಭಾಷೆ", editionByLanguage: "ಜಗತ್ತಿನ ಸುದ್ದಿ, ನಿಮ್ಮ ಭಾಷೆಯಲ್ಲಿ", newsEyebrow: "ಇತ್ತೀಚಿನ ಸುದ್ದಿ",
			latestNews: "ಇತ್ತೀಚಿನ ಸುದ್ದಿ", sourceLanguageNote: "ಉಚಿತ RSS ಮೂಲಗಳಿಂದ ಸುದ್ದಿಗಳನ್ನು ಅವುಗಳ ಮೂಲ ಭಾಷೆಯಲ್ಲಿ ತೋರಿಸಲಾಗುತ್ತದೆ.",
			refresh: "ಮತ್ತೆ ಲೋಡ್ ಮಾಡಿ", loadingNews: "ಸುದ್ದಿ ಲೋಡ್ ಆಗುತ್ತಿದೆ…", noNews: "ಇನ್ನೂ ಯಾವುದೇ ಸುದ್ದಿ ಪೋಸ್ಟ್ ಮಾಡಲಾಗಿಲ್ಲ.",
			newsError: "ಸುದ್ದಿ ಫೀಡ್‌ಗಳು ತಾತ್ಕಾಲಿಕವಾಗಿ ಲಭ್ಯವಿಲ್ಲ.", readStory: "ಸುದ್ದಿ ಓದಿ", latestEdition: "ಇತ್ತೀಚಿನ ಆವೃತ್ತಿ",
			editionHint: "ಓದಲು ಮತ್ತು ಹಂಚಿಕೊಳ್ಳಲು ಗುರುತಿಸಿದ ಸುದ್ದಿಯನ್ನು ಆಯ್ಕೆಮಾಡಿ.", previous: "ಹಿಂದಿನದು", next: "ಮುಂದಿನದು",
			loadingEdition: "ಆವೃತ್ತಿ ಲೋಡ್ ಆಗುತ್ತಿದೆ…", noEdition: "ಇನ್ನೂ ಯಾವುದೇ ಪತ್ರಿಕೆ ಪ್ರಕಟವಾಗಿಲ್ಲ.",
			checkBack: "ಹೊಸ ಆವೃತ್ತಿಗಾಗಿ ಮತ್ತೆ ಭೇಟಿ ನೀಡಿ.", fromEdition: "ಈ ಆವೃತ್ತಿಯಿಂದ",
			facebook: "ಫೇಸ್‌ಬುಕ್", twitter: "ಎಕ್ಸ್ / ಟ್ವಿಟರ್", whatsapp: "ವಾಟ್ಸಾಪ್", copyLink: "ಲಿಂಕ್ ನಕಲಿಸಿ",
			linkCopied: "ಲಿಂಕ್ ನಕಲಾಗಿದೆ.", copyFailed: "ಲಿಂಕ್ ನಕಲಿಸಲಾಗಲಿಲ್ಲ. ಬ್ರೌಸರ್ ವಿಳಾಸ ಪಟ್ಟಿಯಿಂದ ನಕಲಿಸಿ.",
			selectStory: "ಓದಲು ಮತ್ತು ಹಂಚಿಕೊಳ್ಳಲು ಗುರುತಿಸಿದ ಸುದ್ದಿಯನ್ನು ಆಯ್ಕೆಮಾಡಿ.",
			noArticles: "ಈ ಆವೃತ್ತಿಯಲ್ಲಿ ಲಿಂಕ್ ಮಾಡಿದ ಸುದ್ದಿಗಳಿಲ್ಲ.", loadArticleError: "ಸುದ್ದಿ ಲಿಂಕ್‌ಗಳನ್ನು ಲೋಡ್ ಮಾಡಲಾಗಲಿಲ್ಲ.",
			loadPdfError: "ಪತ್ರಿಕೆಯನ್ನು ಲೋಡ್ ಮಾಡಲಾಗಲಿಲ್ಲ.", page: (number, total) => `ಪುಟ ${number} / ${total}`,
			zoomOut: "ಜೂಮ್ ಕಡಿಮೆ", zoomIn: "ಜೂಮ್ ಹೆಚ್ಚಿಸಿ", readArticle: title => `ಸುದ್ದಿ ಓದಿ: ${title}`,
			publishedEditions: "ಪ್ರಕಟಿತ ಆವೃತ್ತಿಗಳು", closeArticle: "ಸುದ್ದಿ ಮುಚ್ಚಿ", croppedArticle: "ಪತ್ರಿಕೆಯಿಂದ ಕತ್ತರಿಸಿದ ಸುದ್ದಿ"
		}
	};

	let language = app.dataset.language || new URLSearchParams(location.search).get("lang") || "en";
	if (!messages[language]) language = "en";
	window.newsI18n = {
		language,
		text(key, ...args) {
			const value = messages[language][key] || messages.en[key] || key;
			return typeof value === "function" ? value(...args) : value;
		}
	};

	document.documentElement.lang = language;
	document.documentElement.dir = language === "ur" ? "rtl" : "ltr";
	document.title = window.newsI18n.text("brand");
	for (const element of document.querySelectorAll("[data-i18n]")) {
		element.textContent = window.newsI18n.text(element.dataset.i18n);
	}
	const today = document.getElementById("today-date");
	if (today) {
		const now = new Date();
		today.dateTime = [
			now.getFullYear(),
			String(now.getMonth() + 1).padStart(2, "0"),
			String(now.getDate()).padStart(2, "0")
		].join("-");
		today.textContent = new Intl.DateTimeFormat(language, {
			weekday: "long", year: "numeric", month: "long", day: "numeric"
		}).format(now);
	}
	document.getElementById("reader-zoom-in")?.setAttribute("aria-label", window.newsI18n.text("zoomIn"));
	document.getElementById("reader-zoom-out")?.setAttribute("aria-label", window.newsI18n.text("zoomOut"));
	document.querySelector('nav[aria-label="Published editions"]')
		?.setAttribute("aria-label", window.newsI18n.text("publishedEditions"));
	document.getElementById("language-picker")
		?.setAttribute("aria-label", window.newsI18n.text("language"));
	document.getElementById("close-modal")
		?.setAttribute("aria-label", window.newsI18n.text("closeArticle"));
	document.getElementById("story-image")
		?.setAttribute("alt", window.newsI18n.text("croppedArticle"));
	const picker = document.getElementById("language-picker");
	if (picker) {
		picker.value = language;
		picker.addEventListener("change", () => {
			const query = new URLSearchParams(location.search);
			query.set("lang", picker.value);
			location.assign(`${location.pathname}?${query.toString()}${location.hash}`);
		});
	}
})();
