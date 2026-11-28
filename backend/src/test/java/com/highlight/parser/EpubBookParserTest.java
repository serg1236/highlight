package com.highlight.parser;

import com.highlight.entity.BookFormat;
import com.highlight.exception.BookParseException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EpubBookParserTest {

	private static final String CONTAINER = """
			<?xml version="1.0"?>
			<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
				<rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles>
			</container>
			""";

	private static final String OPF = """
			<?xml version="1.0"?>
			<package xmlns="http://www.idpf.org/2007/opf" version="3.0">
				<metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
					<dc:title>Pride and Prejudice</dc:title>
					<dc:creator>Jane Austen</dc:creator>
					<dc:language>en</dc:language>
				</metadata>
				<manifest>
					<item id="cover" href="cover.xhtml" media-type="application/xhtml+xml"/>
					<item id="c1" href="text/chapter%201.xhtml" media-type="application/xhtml+xml"/>
					<item id="c2" href="text/../chapter2.xhtml#start" media-type="application/xhtml+xml"/>
					<item id="empty" href="empty.xhtml" media-type="application/xhtml+xml"/>
				</manifest>
				<spine>
					<itemref idref="cover" linear="no"/>
					<itemref idref="c1"/>
					<itemref idref="missing"/>
					<itemref idref="empty"/>
					<itemref idref="c2"/>
				</spine>
			</package>
			""";

	private final EpubBookParser parser = new EpubBookParser();

	@Test
	void format() {
		assertEquals(BookFormat.EPUB, parser.format());
	}

	@Test
	void parse_validEpub_readsSpineInOrder() throws IOException {
		// given
		var files = new LinkedHashMap<String, String>();
		files.put("mimetype", "application/epub+zip");
		files.put("META-INF/container.xml", CONTAINER);
		files.put("OEBPS/content.opf", OPF);
		files.put("OEBPS/cover.xhtml", html("<p>Cover page</p>"));
		files.put("OEBPS/text/chapter 1.xhtml",
				html("<h2>Chapter 1</h2><p>Elizabeth had   <em>dark</em> eyes.</p><p>Second paragraph.</p>"));
		files.put("OEBPS/empty.xhtml", html("<div>   </div>"));
		files.put("OEBPS/chapter2.xhtml", html("<div><div>Darcy wore a coat.</div><div>He left.</div></div>"));
		var chapterOne = "Chapter 1\n\nElizabeth had dark eyes.\n\nSecond paragraph.";
		var chapterTwo = "Darcy wore a coat.\n\nHe left.";
		var text = chapterOne + "\n\n" + chapterTwo;
		var expected = new ParsedBook("Pride and Prejudice", "Jane Austen", "en", text, List.of(
				new ParsedChapter(0, "Chapter 1", 0, chapterOne.length()),
				new ParsedChapter(1, null, chapterOne.length() + 2, text.length())));

		// when
		var actual = parser.parse(zip(files));

		// then
		assertEquals(expected, actual);
	}

	@Test
	void parse_notZip_shouldThrowException() {
		var content = "plain text".getBytes(StandardCharsets.UTF_8);

		assertThrows(BookParseException.class, () -> parser.parse(content));
	}

	@Test
	void parse_missingContainer_shouldThrowException() throws IOException {
		var content = zip(Map.of("OEBPS/content.opf", OPF));

		assertThrows(BookParseException.class, () -> parser.parse(content));
	}

	@Test
	void parse_containerWithoutRootFile_shouldThrowException() throws IOException {
		var content = zip(Map.of("META-INF/container.xml", "<container/>"));

		assertThrows(BookParseException.class, () -> parser.parse(content));
	}

	private static String html(String body) {
		return "<html xmlns=\"http://www.w3.org/1999/xhtml\"><head><title>t</title></head><body>" + body
			   + "</body></html>";
	}

	private static byte[] zip(Map<String, String> files) throws IOException {
		var out = new ByteArrayOutputStream();
		try (var zip = new ZipOutputStream(out)) {
			zip.putNextEntry(new ZipEntry("META-INF/"));
			zip.closeEntry();
			for (var file : files.entrySet()) {
				zip.putNextEntry(new ZipEntry(file.getKey()));
				zip.write(file.getValue().getBytes(StandardCharsets.UTF_8));
				zip.closeEntry();
			}
		}
		return out.toByteArray();
	}
}
