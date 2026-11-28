package com.highlight.parser;

import com.highlight.exception.BookParseException;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Assembles normalized book text chapter by chapter and records chapter offsets.
 */
final class BookTextBuilder {

	static final String PARAGRAPH_SEPARATOR = "\n\n";

	private static final Pattern WHITESPACE = Pattern.compile("[\\s\\u00A0\\u2007\\u202F]+");

	private final StringBuilder text = new StringBuilder();

	private final List<ParsedChapter> chapters = new ArrayList<>();

	static String normalizeParagraph(String paragraph) {
		if (paragraph == null) {
			return "";
		}
		return WHITESPACE.matcher(paragraph).replaceAll(" ").strip();
	}

	/**
	 * Adds a chapter; chapters whose paragraphs are all blank are skipped.
	 */
	void addChapter(String title, List<String> paragraphs) {
		var cleaned = paragraphs.stream()
				.map(BookTextBuilder::normalizeParagraph)
				.filter(paragraph -> !paragraph.isEmpty())
				.toList();
		if (cleaned.isEmpty()) {
			return;
		}
		if (!text.isEmpty()) {
			text.append(PARAGRAPH_SEPARATOR);
		}
		var start = text.length();
		text.append(String.join(PARAGRAPH_SEPARATOR, cleaned));
		var normalizedTitle = normalizeParagraph(title);
		chapters.add(new ParsedChapter(chapters.size(), normalizedTitle.isEmpty() ? null : normalizedTitle, start,
				text.length()));
	}

	ParsedBook build(String title, String author, String language) {
		if (chapters.isEmpty()) {
			throw new BookParseException("The file contains no readable text");
		}
		return new ParsedBook(blankToNull(title), blankToNull(author), blankToNull(language), text.toString(),
				List.copyOf(chapters));
	}

	private static String blankToNull(String value) {
		var normalized = normalizeParagraph(value);
		return normalized.isEmpty() ? null : normalized;
	}
}
