package com.example.news.service;

import java.awt.geom.Point2D;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.apache.pdfbox.contentstream.PDFGraphicsStreamEngine;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.PDImage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.apache.pdfbox.util.Matrix;
import org.springframework.stereotype.Service;

@Service
public class PdfAutoDetectionService {

	private static final int MAX_CANDIDATES = 1000;
	private static final float MIN_GUTTER = 6f;
	private static final float MIN_LEADING = 4f;
	private static final float LEADING_RATIO = 0.9f;
	private static final float HEADLINE_RATIO = 1.2f;
	private static final float MIN_PHOTO_SIZE = 40f;

	public List<Candidate> detect(PDDocument document) throws IOException {
		List<Candidate> candidates = new ArrayList<>();
		for (int pageIndex = 0; pageIndex < document.getNumberOfPages(); pageIndex++) {
			PDPage page = document.getPage(pageIndex);
			PositionCollector collector = new PositionCollector();
			collector.setStartPage(pageIndex + 1);
			collector.setEndPage(pageIndex + 1);
			collector.getText(document);
			ImageCollector images = new ImageCollector(page);
			images.processPage(page);

			List<Item> items = new ArrayList<>(makeLines(collector.positions));
			items.addAll(images.items);
			for (List<Item> region : mergeFragments(segment(items))) {
				for (List<Item> article : splitArticles(region)) {
					addCandidate(page, pageIndex + 1, article, candidates);
					if (candidates.size() >= MAX_CANDIDATES) {
						return List.copyOf(candidates.subList(0, MAX_CANDIDATES));
					}
				}
			}
		}
		return List.copyOf(candidates);
	}

	private List<Item> makeLines(List<TextPosition> positions) {
		List<TextPosition> ordered = positions.stream()
				.sorted(Comparator.comparingDouble(TextPosition::getYDirAdj)
						.thenComparingDouble(TextPosition::getXDirAdj))
				.toList();
		List<List<TextPosition>> lines = new ArrayList<>();
		for (TextPosition position : ordered) {
			List<TextPosition> line = null;
			for (int index = lines.size() - 1; index >= 0; index--) {
				List<TextPosition> possible = lines.get(index);
				float averageY = (float) possible.stream().mapToDouble(TextPosition::getYDirAdj).average().orElse(0);
				float tolerance = Math.max(2.5f, Math.max(position.getHeightDir(), averageHeight(possible)) * 0.55f);
				if (Math.abs(position.getYDirAdj() - averageY) <= tolerance) {
					line = possible;
					break;
				}
				if (averageY < position.getYDirAdj() - 18) {
					break;
				}
			}
			if (line == null) {
				line = new ArrayList<>();
				lines.add(line);
			}
			line.add(position);
		}

		List<Item> result = new ArrayList<>();
		for (List<TextPosition> positionsOnLine : lines) {
			positionsOnLine.sort(Comparator.comparingDouble(TextPosition::getXDirAdj));
			// Text on the same baseline in different columns must not be merged into one line, so split at wide gaps.
			List<TextPosition> segment = new ArrayList<>();
			float previousRight = 0;
			for (TextPosition position : positionsOnLine) {
				String unicode = position.getUnicode();
				if (unicode != null && unicode.isBlank()) {
					continue;
				}
				float x = position.getXDirAdj();
				if (!segment.isEmpty() && x - previousRight > Math.max(5f, position.getFontSizeInPt())) {
					addLine(segment, result);
					segment = new ArrayList<>();
				}
				segment.add(position);
				previousRight = Math.max(previousRight, x + Math.max(0, position.getWidthDirAdj()));
			}
			addLine(segment, result);
		}
		return result;
	}

	private void addLine(List<TextPosition> positions, List<Item> result) {
		if (positions.isEmpty()) {
			return;
		}
		StringBuilder text = new StringBuilder();
		float left = Float.MAX_VALUE;
		float right = 0;
		float top = Float.MAX_VALUE;
		float bottom = 0;
		float fontSize = 0;
		float previousRight = -1;
		for (TextPosition position : positions) {
			String unicode = position.getUnicode();
			// Glyphs without a Unicode mapping (common in newspaper PDFs) still occupy space, so keep their geometry.
			boolean mapped = unicode != null;
			float x = position.getXDirAdj();
			float width = Math.max(0, position.getWidthDirAdj());
			if (mapped && previousRight >= 0 && x - previousRight > Math.max(1.5f, position.getFontSizeInPt() * 0.18f)
					&& !Character.isWhitespace(unicode.charAt(0)) && text.length() > 0) {
				text.append(' ');
			}
			if (mapped) {
				text.append(unicode);
			}
			left = Math.min(left, x);
			right = Math.max(right, x + width);
			top = Math.min(top, position.getYDirAdj() - position.getHeightDir());
			bottom = Math.max(bottom, position.getYDirAdj());
			fontSize = Math.max(fontSize, position.getFontSizeInPt());
			previousRight = Math.max(previousRight, x + width);
		}
		String normalized = text.toString().replaceAll("\\s+", " ").strip();
		if (left < right && top < bottom) {
			result.add(new Item(left, right, top, bottom, fontSize, normalized, false));
		}
	}

	/**
	 * Recursively splits the page at the widest whitespace gutter or leading gap, so text in different columns
	 * and areas blocked by photos end up in separate regions.
	 */
	private List<List<Item>> segment(List<Item> items) {
		List<List<Item>> leaves = new ArrayList<>();
		segmentInto(items, leaves);
		return leaves;
	}

	private void segmentInto(List<Item> items, List<List<Item>> leaves) {
		Cut cut = findCut(items);
		if (cut == null) {
			leaves.add(items);
			return;
		}
		segmentInto(cut.first(), leaves);
		segmentInto(cut.second(), leaves);
	}

	private Cut findCut(List<Item> items) {
		if (items.size() < 2) {
			return null;
		}
		Cut best = null;
		float bestScore = 1f;

		List<Item> byLeft = items.stream().sorted(Comparator.comparingDouble(Item::left)).toList();
		float maxRight = byLeft.get(0).right();
		for (int index = 1; index < byLeft.size(); index++) {
			maxRight = Math.max(maxRight, byLeft.get(index - 1).right());
			Item next = byLeft.get(index);
			float gap = next.left() - maxRight;
			float threshold = Math.max(MIN_GUTTER, Math.max(byLeft.get(index - 1).fontSize(), next.fontSize()));
			if (gap / threshold >= bestScore) {
				bestScore = gap / threshold;
				best = new Cut(new ArrayList<>(byLeft.subList(0, index)), new ArrayList<>(byLeft.subList(index, byLeft.size())));
			}
		}

		List<Item> byTop = items.stream().sorted(Comparator.comparingDouble(Item::top)).toList();
		float maxBottom = byTop.get(0).bottom();
		for (int index = 1; index < byTop.size(); index++) {
			maxBottom = Math.max(maxBottom, byTop.get(index - 1).bottom());
			Item next = byTop.get(index);
			float gap = next.top() - maxBottom;
			float threshold = Math.max(MIN_LEADING, LEADING_RATIO * Math.max(byTop.get(index - 1).fontSize(), next.fontSize()));
			if (gap / threshold >= bestScore) {
				bestScore = gap / threshold;
				best = new Cut(new ArrayList<>(byTop.subList(0, index)), new ArrayList<>(byTop.subList(index, byTop.size())));
			}
		}
		return best;
	}

	/**
	 * Drops photo-only regions and attaches one-line fragments (such as a headline separated from its body)
	 * to the block directly below them.
	 */
	private List<List<Item>> mergeFragments(List<List<Item>> leaves) {
		List<List<Item>> merged = new ArrayList<>();
		for (List<Item> leaf : leaves) {
			List<Item> text = new ArrayList<>(leaf.stream().filter(item -> !item.image()).toList());
			if (!text.isEmpty()) {
				merged.add(text);
			}
		}
		boolean changed = true;
		while (changed) {
			changed = false;
			for (int fragmentIndex = 0; fragmentIndex < merged.size() && !changed; fragmentIndex++) {
				List<Item> fragment = merged.get(fragmentIndex);
				if (fragment.size() != 1) {
					continue;
				}
				Item head = fragment.get(0);
				for (int targetIndex = 0; targetIndex < merged.size(); targetIndex++) {
					if (targetIndex == fragmentIndex) {
						continue;
					}
					List<Item> target = merged.get(targetIndex);
					Box targetBox = boundsOf(target);
					Item firstLine = target.stream().min(Comparator.comparingDouble(Item::top)).orElseThrow();
					float gap = firstLine.top() - head.bottom();
					boolean overlaps = head.left() < targetBox.right() && head.right() > targetBox.left();
					if (overlaps && gap >= -2 && gap <= 2 * Math.max(head.fontSize(), firstLine.fontSize())) {
						target.addAll(fragment);
						merged.remove(fragmentIndex);
						changed = true;
						break;
					}
				}
			}
		}
		return merged;
	}

	/**
	 * Starts a new article at a headline: a noticeably larger line that sits below a clear gap.
	 */
	private List<List<Item>> splitArticles(List<Item> region) {
		List<Item> ordered = region.stream()
				.sorted(Comparator.comparingDouble(Item::top).thenComparingDouble(Item::left))
				.toList();
		float bodyFont = medianFontSize(ordered);
		List<List<Item>> articles = new ArrayList<>();
		List<Item> current = new ArrayList<>();
		Item previous = null;
		for (Item item : ordered) {
			boolean headline = previous != null
					&& item.fontSize() >= bodyFont * HEADLINE_RATIO
					&& item.top() - previous.bottom() >= item.fontSize() * 0.5f;
			if (headline && !current.isEmpty()) {
				articles.add(current);
				current = new ArrayList<>();
			}
			current.add(item);
			previous = item;
		}
		if (!current.isEmpty()) {
			articles.add(current);
		}
		return articles;
	}

	private float medianFontSize(List<Item> items) {
		List<Float> sizes = items.stream().map(Item::fontSize).filter(size -> size > 0).sorted().toList();
		return sizes.isEmpty() ? 0 : sizes.get(sizes.size() / 2);
	}

	private Box boundsOf(List<Item> items) {
		float left = Float.MAX_VALUE;
		float right = -Float.MAX_VALUE;
		float top = Float.MAX_VALUE;
		float bottom = -Float.MAX_VALUE;
		for (Item item : items) {
			left = Math.min(left, item.left());
			right = Math.max(right, item.right());
			top = Math.min(top, item.top());
			bottom = Math.max(bottom, item.bottom());
		}
		return new Box(left, right, top, bottom);
	}

	private void addCandidate(PDPage page, int pageNumber, List<Item> items, List<Candidate> result) {
		List<Item> lines = items.stream()
				.filter(item -> !item.image())
				.sorted(Comparator.comparingDouble(Item::top).thenComparingDouble(Item::left))
				.toList();
		if (lines.size() < 3) {
			return;
		}
		Box box = boundsOf(lines);
		if (box.right() - box.left() < 45 || box.bottom() - box.top() < 28) {
			return;
		}
		String title = lines.stream().map(Item::text).filter(text -> !text.isBlank()).findFirst()
				.orElse("Untitled article on page " + pageNumber);
		if (title.length() > 120) {
			title = title.substring(0, 117) + "...";
		}
		var crop = page.getCropBox();
		float pdfX = crop.getLowerLeftX() + box.left();
		float pdfY = crop.getLowerLeftY() + crop.getHeight() - box.bottom();
		result.add(new Candidate(pageNumber, pdfX, pdfY, box.right() - box.left(), box.bottom() - box.top(), title));
	}

	private float averageHeight(List<TextPosition> positions) {
		return (float) positions.stream().mapToDouble(TextPosition::getHeightDir).average().orElse(0);
	}

	public record Candidate(int pageNumber, float pdfX, float pdfY, float pdfWidth, float pdfHeight,
			String suggestedTitle) {
	}

	private record Item(float left, float right, float top, float bottom, float fontSize, String text, boolean image) {
	}

	private record Box(float left, float right, float top, float bottom) {
	}

	private record Cut(List<Item> first, List<Item> second) {
	}

	/**
	 * Collects placed images (photos) so they can act as obstacles that article boxes must not cross.
	 */
	private static final class ImageCollector extends PDFGraphicsStreamEngine {
		private final List<Item> items = new ArrayList<>();

		private ImageCollector(PDPage page) {
			super(page);
		}

		@Override
		public void drawImage(PDImage image) {
			Matrix ctm = getGraphicsState().getCurrentTransformationMatrix();
			float minX = Float.MAX_VALUE;
			float maxX = -Float.MAX_VALUE;
			float minY = Float.MAX_VALUE;
			float maxY = -Float.MAX_VALUE;
			for (float[] corner : new float[][] { { 0, 0 }, { 1, 0 }, { 0, 1 }, { 1, 1 } }) {
				Point2D.Float point = ctm.transformPoint(corner[0], corner[1]);
				minX = Math.min(minX, point.x);
				maxX = Math.max(maxX, point.x);
				minY = Math.min(minY, point.y);
				maxY = Math.max(maxY, point.y);
			}
			PDRectangle crop = getPage().getCropBox();
			float left = minX - crop.getLowerLeftX();
			float right = maxX - crop.getLowerLeftX();
			float top = crop.getUpperRightY() - maxY;
			float bottom = crop.getUpperRightY() - minY;
			if (right - left >= MIN_PHOTO_SIZE && bottom - top >= MIN_PHOTO_SIZE) {
				items.add(new Item(left, right, top, bottom, 0, "", true));
			}
		}

		@Override
		public void appendRectangle(Point2D p0, Point2D p1, Point2D p2, Point2D p3) {
		}

		@Override
		public void clip(int windingRule) {
		}

		@Override
		public void moveTo(float x, float y) {
		}

		@Override
		public void lineTo(float x, float y) {
		}

		@Override
		public void curveTo(float x1, float y1, float x2, float y2, float x3, float y3) {
		}

		@Override
		public Point2D getCurrentPoint() {
			return null;
		}

		@Override
		public void closePath() {
		}

		@Override
		public void endPath() {
		}

		@Override
		public void strokePath() {
		}

		@Override
		public void fillPath(int windingRule) {
		}

		@Override
		public void fillAndStrokePath(int windingRule) {
		}

		@Override
		public void shadingFill(COSName shadingName) {
		}
	}

	private static final class PositionCollector extends PDFTextStripper {
		private final List<TextPosition> positions = new ArrayList<>();

		private PositionCollector() throws IOException {
			super();
			setSortByPosition(true);
		}

		@Override
		protected void writeString(String text, List<TextPosition> textPositions) {
			positions.addAll(textPositions);
		}
	}
}
