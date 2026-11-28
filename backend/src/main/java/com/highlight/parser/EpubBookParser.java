package com.highlight.parser;

import com.highlight.entity.BookFormat;
import com.highlight.exception.BookParseException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipInputStream;

/**
 * EPUB 2/3: reads the OPF package, walks the spine in reading order and treats each spine document as a chapter.
 */
@Component
public class EpubBookParser implements BookParser {

	private static final long MAX_UNCOMPRESSED_BYTES = 300L * 1024 * 1024;

	private static final String CONTAINER_PATH = "META-INF/container.xml";

	private static final String TEXT_ELEMENTS = "h1, h2, h3, h4, h5, h6, p";

	private static final String HEADING_ELEMENTS = "h1, h2, h3";

	@Override
	public BookFormat format() {
		return BookFormat.EPUB;
	}

	@Override
	public ParsedBook parse(byte[] content) {
		var entries = unzip(content);
		var opfPath = findOpfPath(entries);
		var opf = parseXml(requireEntry(entries, opfPath));
		var baseDir = opfPath.contains("/") ? opfPath.substring(0, opfPath.lastIndexOf('/') + 1) : "";

		var hrefById = new HashMap<String, String>();
		for (var item : opf.select("manifest > item")) {
			hrefById.put(item.attr("id"), item.attr("href"));
		}

		var builder = new BookTextBuilder();
		for (var itemRef : opf.select("spine > itemref")) {
			var href = hrefById.get(itemRef.attr("idref"));
			if (href == null || "no".equalsIgnoreCase(itemRef.attr("linear"))) {
				continue;
			}
			var document = entries.get(resolve(baseDir, href));
			if (document != null) {
				addChapter(builder, Jsoup.parse(new String(document, StandardCharsets.UTF_8)));
			}
		}
		return builder.build(metadata(opf, "dc|title"), metadata(opf, "dc|creator"), metadata(opf, "dc|language"));
	}

	private static void addChapter(BookTextBuilder builder, Document document) {
		var body = document.body();
		var heading = body.selectFirst(HEADING_ELEMENTS);
		var title = heading != null ? heading.text() : null;
		var paragraphs = new ArrayList<String>();
		for (var element : body.select(TEXT_ELEMENTS)) {
			paragraphs.add(element.text());
		}
		if (paragraphs.isEmpty()) {
			paragraphs.addAll(leafBlockTexts(body));
		}
		builder.addChapter(title, paragraphs);
	}

	/**
	 * Fallback for documents that keep text in plain divs instead of paragraphs.
	 */
	private static List<String> leafBlockTexts(Element body) {
		var leafDivs = body.select("div").stream().filter(div -> div.select("div").size() == 1).toList();
		if (leafDivs.isEmpty()) {
			return List.of(body.text());
		}
		return leafDivs.stream().map(Element::text).toList();
	}

	private static String findOpfPath(Map<String, byte[]> entries) {
		var container = parseXml(requireEntry(entries, CONTAINER_PATH));
		var rootFile = container.selectFirst("rootfile");
		if (rootFile == null || rootFile.attr("full-path").isBlank()) {
			throw new BookParseException("EPUB container does not reference a package document");
		}
		return rootFile.attr("full-path");
	}

	private static String metadata(Document opf, String selector) {
		var element = opf.selectFirst("metadata " + selector);
		return element != null ? element.text() : null;
	}

	private static String resolve(String baseDir, String href) {
		var withoutFragment = href.contains("#") ? href.substring(0, href.indexOf('#')) : href;
		var decoded = URLDecoder.decode(withoutFragment.replace("+", "%2B"), StandardCharsets.UTF_8);
		return Path.of(baseDir + decoded).normalize().toString().replace('\\', '/');
	}

	private static Document parseXml(byte[] xml) {
		return Jsoup.parse(new String(xml, StandardCharsets.UTF_8), "", Parser.xmlParser());
	}

	private static byte[] requireEntry(Map<String, byte[]> entries, String name) {
		var entry = entries.get(name);
		if (entry == null) {
			throw new BookParseException("EPUB is missing " + name);
		}
		return entry;
	}

	private static Map<String, byte[]> unzip(byte[] content) {
		var entries = new HashMap<String, byte[]>();
		var total = 0L;
		try (var zip = new ZipInputStream(new ByteArrayInputStream(content), StandardCharsets.UTF_8)) {
			for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
				if (entry.isDirectory()) {
					continue;
				}
				var bytes = readLimited(zip, MAX_UNCOMPRESSED_BYTES - total);
				total += bytes.length;
				entries.put(entry.getName(), bytes);
			}
		} catch (IOException e) {
			throw new BookParseException("File is not a valid EPUB archive", e);
		}
		if (entries.isEmpty()) {
			throw new BookParseException("File is not a valid EPUB archive");
		}
		return entries;
	}

	private static byte[] readLimited(ZipInputStream zip, long remaining) throws IOException {
		var out = new ByteArrayOutputStream();
		var buffer = new byte[8192];
		for (int read = zip.read(buffer); read != -1; read = zip.read(buffer)) {
			out.write(buffer, 0, read);
			if (out.size() > remaining) {
				throw new BookParseException("EPUB archive is too large when uncompressed");
			}
		}
		return out.toByteArray();
	}
}
