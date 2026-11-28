package com.highlight.parser;

import com.highlight.entity.BookFormat;
import com.highlight.exception.BookParseException;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.regex.Pattern;

/**
 * Plain UTF-8 text. Paragraphs are separated by blank lines; hard-wrapped lines inside a paragraph are joined.
 * Chapters start at short heading lines such as "Chapter 1", "CHAPTER IV." or "Розділ 3".
 * Project Gutenberg headers and licence footers are removed.
 */
@Component
public class TxtBookParser implements BookParser {

	private static final int MAX_HEADING_LENGTH = 100;

	private static final Pattern WORD_HEADING = Pattern.compile(
			"(?iu)^(chapter|part|book|розділ|частина)\\s+([0-9]+|[ivxlcdm]+|\\p{L}+)\\b.*$");

	private static final Pattern ROMAN_HEADING = Pattern.compile("^[IVXLC]+\\.?$");

	private static final Pattern BLANK_LINES = Pattern.compile("\\n\\s*\\n");

	private static final Pattern GUTENBERG_TITLE = Pattern.compile("(?m)^Title:\\s*(.+)$");

	private static final Pattern GUTENBERG_AUTHOR = Pattern.compile("(?m)^Author:\\s*(.+)$");

	private static final Pattern GUTENBERG_LANGUAGE = Pattern.compile("(?m)^Language:\\s*(.+)$");

	private static final String GUTENBERG_START = "*** START OF";

	private static final String GUTENBERG_END = "*** END OF";

	@Override
	public BookFormat format() {
		return BookFormat.TXT;
	}

	@Override
	public ParsedBook parse(byte[] content) {
		var raw = decodeUtf8(content).replace("\r\n", "\n").replace('\r', '\n');
		if (raw.startsWith("﻿")) {
			raw = raw.substring(1);
		}
		var header = gutenbergHeader(raw);
		var body = stripGutenbergFrame(raw);

		var builder = new BookTextBuilder();
		String chapterTitle = null;
		var paragraphs = new ArrayList<String>();
		for (var block : BLANK_LINES.split(body)) {
			var paragraph = BookTextBuilder.normalizeParagraph(block);
			if (paragraph.isEmpty()) {
				continue;
			}
			if (isHeading(paragraph)) {
				builder.addChapter(chapterTitle, paragraphs);
				chapterTitle = paragraph;
				paragraphs = new ArrayList<>();
			}
			paragraphs.add(paragraph);
		}
		builder.addChapter(chapterTitle, paragraphs);
		return builder.build(find(GUTENBERG_TITLE, header), find(GUTENBERG_AUTHOR, header),
				find(GUTENBERG_LANGUAGE, header));
	}

	private static String decodeUtf8(byte[] content) {
		try {
			return StandardCharsets.UTF_8.newDecoder()
					.onMalformedInput(CodingErrorAction.REPORT)
					.onUnmappableCharacter(CodingErrorAction.REPORT)
					.decode(ByteBuffer.wrap(content))
					.toString();
		} catch (CharacterCodingException e) {
			throw new BookParseException("TXT files must be UTF-8 encoded", e);
		}
	}

	private static boolean isHeading(String paragraph) {
		return paragraph.length() <= MAX_HEADING_LENGTH
			   && (WORD_HEADING.matcher(paragraph).matches() || ROMAN_HEADING.matcher(paragraph).matches());
	}

	private static String gutenbergHeader(String raw) {
		var start = raw.indexOf(GUTENBERG_START);
		return start < 0 ? "" : raw.substring(0, start);
	}

	private static String stripGutenbergFrame(String raw) {
		var body = raw;
		var start = body.indexOf(GUTENBERG_START);
		if (start >= 0) {
			var lineEnd = body.indexOf('\n', start);
			body = lineEnd < 0 ? "" : body.substring(lineEnd + 1);
		}
		var end = body.indexOf(GUTENBERG_END);
		return end < 0 ? body : body.substring(0, end);
	}

	private static String find(Pattern pattern, String text) {
		var matcher = pattern.matcher(text);
		return matcher.find() ? matcher.group(1) : null;
	}
}
