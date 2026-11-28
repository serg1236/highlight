package com.highlight.parser;

import com.highlight.entity.BookFormat;

public interface BookParser {

	BookFormat format();

	/**
	 * @throws com.highlight.exception.BookParseException when the content is malformed or has no text
	 */
	ParsedBook parse(byte[] content);
}
