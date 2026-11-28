package com.highlight.service.support;

import com.highlight.entity.BookChapter;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Packs whole chapters into chunks under a token budget; oversized chapters are split at paragraph boundaries.
 */
@Component
public class BookChunker {

	private static final double LATIN_CHARS_PER_TOKEN = 4.0;

	/**
	 * Cyrillic scripts such as Ukrainian need about twice as many tokens per character.
	 */
	private static final double CYRILLIC_CHARS_PER_TOKEN = 2.5;

	private static final double CYRILLIC_SHARE_THRESHOLD = 0.3;

	private static final String PARAGRAPH_SEPARATOR = "\n\n";

	/**
	 * Rough token count of the text, without calling the API.
	 */
	public long estimateTokens(String text) {
		return Math.round(text.length() / charsPerToken(text));
	}

	public List<BookChunk> split(String text, List<BookChapter> chapters, int maxTokens) {
		var maxChars = (int) (maxTokens * charsPerToken(text));
		var chunks = new ArrayList<BookChunk>();
		var current = new ArrayList<ChunkSegment>();
		var currentChars = 0;
		for (var segment : segments(text, chapters, maxChars)) {
			if (!current.isEmpty() && currentChars + segment.length() > maxChars) {
				chunks.add(toChunk(chunks.size(), current));
				current = new ArrayList<>();
				currentChars = 0;
			}
			current.add(segment);
			currentChars += segment.length();
		}
		if (!current.isEmpty()) {
			chunks.add(toChunk(chunks.size(), current));
		}
		return chunks;
	}

	private static List<ChunkSegment> segments(String text, List<BookChapter> chapters, int maxChars) {
		var segments = new ArrayList<ChunkSegment>();
		for (var chapter : chapters) {
			var pieceStart = chapter.getStartOffset();
			var end = chapter.getEndOffset();
			var continuation = false;
			while (end - pieceStart > maxChars) {
				var cut = cutPosition(text, pieceStart, pieceStart + maxChars);
				segments.add(new ChunkSegment(chapter.getChapterIndex(), chapter.getTitle(), pieceStart, cut,
						continuation));
				continuation = true;
				pieceStart = skipWhitespace(text, cut, end);
			}
			if (pieceStart < end) {
				segments.add(new ChunkSegment(chapter.getChapterIndex(), chapter.getTitle(), pieceStart, end,
						continuation));
			}
		}
		return segments;
	}

	private static int cutPosition(String text, int start, int limit) {
		var paragraphBreak = text.lastIndexOf(PARAGRAPH_SEPARATOR, limit);
		if (paragraphBreak > start) {
			return paragraphBreak;
		}
		var space = text.lastIndexOf(' ', limit);
		return space > start ? space : limit;
	}

	private static int skipWhitespace(String text, int from, int end) {
		var position = from;
		while (position < end && Character.isWhitespace(text.charAt(position))) {
			position++;
		}
		return position;
	}

	private static BookChunk toChunk(int index, List<ChunkSegment> segments) {
		return new BookChunk(index, segments.getFirst().startOffset(), segments.getLast().endOffset(),
				List.copyOf(segments));
	}

	private static double charsPerToken(String text) {
		long letters = 0;
		long cyrillic = 0;
		for (var i = 0; i < text.length(); i++) {
			var c = text.charAt(i);
			if (Character.isLetter(c)) {
				letters++;
				if (Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CYRILLIC) {
					cyrillic++;
				}
			}
		}
		var cyrillicShare = letters == 0 ? 0 : (double) cyrillic / letters;
		return cyrillicShare > CYRILLIC_SHARE_THRESHOLD ? CYRILLIC_CHARS_PER_TOKEN : LATIN_CHARS_PER_TOKEN;
	}
}
