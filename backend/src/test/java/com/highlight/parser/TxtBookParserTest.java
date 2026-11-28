package com.highlight.parser;

import com.highlight.entity.BookFormat;
import com.highlight.exception.BookParseException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TxtBookParserTest {

	private final TxtBookParser parser = new TxtBookParser();

	@Test
	void format() {
		assertEquals(BookFormat.TXT, parser.format());
	}

	@Test
	void parse_gutenbergText_stripsFrameAndSplitsChapters() {
		// given
		var raw = """
				﻿The Project Gutenberg eBook of Sample Tale\r
				\r
				Title: Sample Tale\r
				Author: Jane Writer\r
				Language: English\r
				\r
				*** START OF THE PROJECT GUTENBERG EBOOK SAMPLE TALE ***\r
				\r
				Preface line\r
				wrapped here.\r
				\r
				CHAPTER I.\r
				\r
				It was a dark\r
				night.\r
				\r
				   \r
				CHAPTER II.\r
				\r
				Morning came.\r
				\r
				*** END OF THE PROJECT GUTENBERG EBOOK SAMPLE TALE ***\r
				License text.\r
				""";
		var preface = "Preface line wrapped here.";
		var chapterOne = "CHAPTER I.\n\nIt was a dark night.";
		var chapterTwo = "CHAPTER II.\n\nMorning came.";
		var text = preface + "\n\n" + chapterOne + "\n\n" + chapterTwo;
		var chapterTwoStart = text.length() - chapterTwo.length();
		var expected = new ParsedBook("Sample Tale", "Jane Writer", "English", text, List.of(
				new ParsedChapter(0, null, 0, preface.length()),
				new ParsedChapter(1, "CHAPTER I.", preface.length() + 2, chapterTwoStart - 2),
				new ParsedChapter(2, "CHAPTER II.", chapterTwoStart, text.length())));

		// when
		var actual = parser.parse(raw.getBytes(StandardCharsets.UTF_8));

		// then
		assertEquals(expected, actual);
	}

	@Test
	void parse_ukrainianHeadings_splitsChapters() {
		// given
		var raw = "Розділ 1\n\nПерший текст.\n\nРозділ 2\n\nДругий текст.";
		var expected = new ParsedBook(null, null, null, raw, List.of(
				new ParsedChapter(0, "Розділ 1", 0, 23),
				new ParsedChapter(1, "Розділ 2", 25, raw.length())));

		// when
		var actual = parser.parse(raw.getBytes(StandardCharsets.UTF_8));

		// then
		assertEquals(expected, actual);
	}

	@Test
	void parse_longLineStartingWithChapter_isNotHeading() {
		// given
		var paragraph = "Chapter one of his life began " + "very ".repeat(20) + "slowly.";
		var expected = new ParsedBook(null, null, null, paragraph.strip(),
				List.of(new ParsedChapter(0, null, 0, paragraph.strip().length())));

		// when
		var actual = parser.parse(paragraph.getBytes(StandardCharsets.UTF_8));

		// then
		assertEquals(expected, actual);
	}

	@Test
	void parse_invalidUtf8_shouldThrowException() {
		var content = new byte[]{(byte) 0xC3, (byte) 0x28};

		assertThrows(BookParseException.class, () -> parser.parse(content));
	}

	@Test
	void parse_blankText_shouldThrowException() {
		var content = " \n\n \n".getBytes(StandardCharsets.UTF_8);

		assertThrows(BookParseException.class, () -> parser.parse(content));
	}
}
