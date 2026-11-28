package com.highlight.parser;

import com.highlight.entity.BookFormat;
import com.highlight.exception.BookParseException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Fb2BookParserTest {

	private final Fb2BookParser parser = new Fb2BookParser();

	@Test
	void format() {
		assertEquals(BookFormat.FB2, parser.format());
	}

	@Test
	void parse_nestedSections_innermostSectionsBecomeChapters() throws IOException {
		// given
		byte[] content;
		try (var stream = getClass().getResourceAsStream("sample.fb2")) {
			content = stream.readAllBytes();
		}
		var partIntro = "Part One\n\nPart intro.";
		var chapterOne = "Chapter 1\n\nMavka had long green hair.\n\nLine one\n\nLine two";
		var chapterTwo = "Chapter 2\n\nLukash wore a white shirt.";
		var text = partIntro + "\n\n" + chapterOne + "\n\n" + chapterTwo;
		var chapterOneStart = partIntro.length() + 2;
		var chapterTwoStart = chapterOneStart + chapterOne.length() + 2;
		var expected = new ParsedBook("Forest Song", "Lesya Writer", "uk", text, List.of(
				new ParsedChapter(0, "Part One", 0, partIntro.length()),
				new ParsedChapter(1, "Chapter 1", chapterOneStart, chapterOneStart + chapterOne.length()),
				new ParsedChapter(2, "Chapter 2", chapterTwoStart, text.length())));

		// when
		var actual = parser.parse(content);

		// then
		assertEquals(expected, actual);
	}

	@Test
	void parse_bodyWithoutSections_singleChapter() {
		// given
		var xml = """
				<FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0">
					<body><p>Only text.</p></body>
				</FictionBook>
				""";
		var expected = new ParsedBook(null, null, null, "Only text.", List.of(new ParsedChapter(0, null, 0, 10)));

		// when
		var actual = parser.parse(xml.getBytes(StandardCharsets.UTF_8));

		// then
		assertEquals(expected, actual);
	}

	@Test
	void parse_malformedXml_shouldThrowException() {
		var content = "<FictionBook><body>".getBytes(StandardCharsets.UTF_8);

		assertThrows(BookParseException.class, () -> parser.parse(content));
	}

	@Test
	void parse_doctypeDeclaration_shouldThrowException() {
		var content = """
				<?xml version="1.0"?>
				<!DOCTYPE foo [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
				<FictionBook><body><p>&xxe;</p></body></FictionBook>
				""".getBytes(StandardCharsets.UTF_8);

		assertThrows(BookParseException.class, () -> parser.parse(content));
	}

	@Test
	void parse_noBody_shouldThrowException() {
		var content = "<FictionBook><description/></FictionBook>".getBytes(StandardCharsets.UTF_8);

		assertThrows(BookParseException.class, () -> parser.parse(content));
	}
}
