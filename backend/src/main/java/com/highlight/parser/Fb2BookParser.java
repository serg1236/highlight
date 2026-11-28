package com.highlight.parser;

import com.highlight.entity.BookFormat;
import com.highlight.exception.BookParseException;
import org.springframework.stereotype.Component;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * FictionBook 2: the main body's innermost sections become chapters; notes bodies are skipped.
 */
@Component
public class Fb2BookParser implements BookParser {

	private static final Set<String> TEXT_ELEMENTS = Set.of("p", "v", "subtitle", "text-author");

	@Override
	public BookFormat format() {
		return BookFormat.FB2;
	}

	@Override
	public ParsedBook parse(byte[] content) {
		var root = parseXml(content).getDocumentElement();
		var titleInfo = child(root, "description").flatMap(description -> child(description, "title-info"));

		var builder = new BookTextBuilder();
		var body = mainBody(root);
		addSections(builder, body);
		return builder.build(
				titleInfo.flatMap(info -> child(info, "book-title")).map(Element::getTextContent).orElse(null),
				titleInfo.flatMap(info -> child(info, "author")).map(Fb2BookParser::authorName).orElse(null),
				titleInfo.flatMap(info -> child(info, "lang")).map(Element::getTextContent).orElse(null));
	}

	private static void addSections(BookTextBuilder builder, Element container) {
		var sections = children(container, "section").toList();
		if (sections.isEmpty()) {
			builder.addChapter(sectionTitle(container), textOf(container));
			return;
		}
		var preamble = children(container)
				.filter(element -> !"section".equals(element.getLocalName()))
				.flatMap(element -> textOf(element).stream())
				.toList();
		if (!preamble.isEmpty() && "section".equals(container.getLocalName())) {
			builder.addChapter(sectionTitle(container), preamble);
		}
		sections.forEach(section -> addSections(builder, section));
	}

	private static List<String> textOf(Element element) {
		var paragraphs = new ArrayList<String>();
		collectText(element, paragraphs);
		return paragraphs;
	}

	private static void collectText(Element element, List<String> paragraphs) {
		if (TEXT_ELEMENTS.contains(element.getLocalName())) {
			paragraphs.add(element.getTextContent());
			return;
		}
		children(element).forEach(child -> collectText(child, paragraphs));
	}

	private static String sectionTitle(Element section) {
		return child(section, "title")
				.map(title -> String.join(" ", textOf(title)))
				.orElse(null);
	}

	private static Element mainBody(Element root) {
		var bodies = children(root, "body").toList();
		if (bodies.isEmpty()) {
			throw new BookParseException("FB2 file has no body");
		}
		return bodies.stream()
				.filter(body -> !body.hasAttribute("name"))
				.findFirst()
				.orElse(bodies.getFirst());
	}

	private static String authorName(Element author) {
		return Stream.of("first-name", "middle-name", "last-name")
				.map(name -> child(author, name).map(Element::getTextContent).orElse(""))
				.filter(part -> !part.isBlank())
				.map(String::strip)
				.collect(Collectors.joining(" "));
	}

	private static Optional<Element> child(Element parent, String localName) {
		return children(parent, localName).findFirst();
	}

	private static Stream<Element> children(Element parent, String localName) {
		return children(parent).filter(element -> localName.equals(element.getLocalName()));
	}

	private static Stream<Element> children(Element parent) {
		var nodes = parent.getChildNodes();
		var result = new ArrayList<Element>();
		for (int i = 0; i < nodes.getLength(); i++) {
			if (nodes.item(i).getNodeType() == Node.ELEMENT_NODE) {
				result.add((Element) nodes.item(i));
			}
		}
		return result.stream();
	}

	private static org.w3c.dom.Document parseXml(byte[] content) {
		try {
			var factory = DocumentBuilderFactory.newInstance();
			factory.setNamespaceAware(true);
			factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
			factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			factory.setExpandEntityReferences(false);
			var documentBuilder = factory.newDocumentBuilder();
			documentBuilder.setErrorHandler(null);
			return documentBuilder.parse(new ByteArrayInputStream(content));
		} catch (ParserConfigurationException | SAXException | IOException e) {
			throw new BookParseException("File is not a valid FB2 document", e);
		}
	}
}
