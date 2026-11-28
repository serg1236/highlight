package com.highlight.service.support;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Optional;

/**
 * Finds a model-reported quote in the book text. Offsets always come from here, never from the model.
 * Matching is exact first, then tolerant of case, whitespace, quote marks and dashes.
 */
@Component
public class QuoteLocator {

	private static final int MIN_QUOTE_LENGTH = 3;

	private static final String TRIM_CHARS = "\"'“”‘’«»„….";

	public Optional<TextSpan> locate(String text, int from, int to, String quote) {
		var cleaned = trimQuote(quote);
		if (cleaned.length() < MIN_QUOTE_LENGTH) {
			return Optional.empty();
		}
		var exact = text.indexOf(cleaned, from);
		if (exact >= 0 && exact + cleaned.length() <= to) {
			return Optional.of(new TextSpan(exact, exact + cleaned.length()));
		}
		return locateNormalized(text, from, to, cleaned);
	}

	private static Optional<TextSpan> locateNormalized(String text, int from, int to, String quote) {
		var region = new StringBuilder();
		var originalIndex = new ArrayList<Integer>();
		for (var i = from; i < to; i++) {
			var c = normalizeChar(text.charAt(i));
			if (c == ' ' && (region.isEmpty() || region.charAt(region.length() - 1) == ' ')) {
				continue;
			}
			region.append(c);
			originalIndex.add(i);
		}
		var normalizedQuote = normalize(quote);
		var found = region.indexOf(normalizedQuote);
		if (found < 0) {
			return Optional.empty();
		}
		var start = originalIndex.get(found);
		var end = originalIndex.get(found + normalizedQuote.length() - 1) + 1;
		return Optional.of(new TextSpan(start, end));
	}

	private static String normalize(String value) {
		var result = new StringBuilder();
		for (var i = 0; i < value.length(); i++) {
			var c = normalizeChar(value.charAt(i));
			if (c == ' ' && (result.isEmpty() || result.charAt(result.length() - 1) == ' ')) {
				continue;
			}
			result.append(c);
		}
		return result.toString().strip();
	}

	private static char normalizeChar(char c) {
		if (Character.isWhitespace(c) || Character.isSpaceChar(c)) {
			return ' ';
		}
		return switch (c) {
			case '‘', '’', 'ʼ', '`' -> '\'';
			case '“', '”', '«', '»', '„' -> '"';
			case '–', '—', '‑', '‒' -> '-';
			default -> Character.toLowerCase(c);
		};
	}

	private static String trimQuote(String quote) {
		if (quote == null) {
			return "";
		}
		var start = 0;
		var end = quote.length();
		while (start < end && isTrimmable(quote.charAt(start))) {
			start++;
		}
		while (end > start && isTrimmable(quote.charAt(end - 1))) {
			end--;
		}
		return quote.substring(start, end);
	}

	private static boolean isTrimmable(char c) {
		return Character.isWhitespace(c) || TRIM_CHARS.indexOf(c) >= 0;
	}
}
