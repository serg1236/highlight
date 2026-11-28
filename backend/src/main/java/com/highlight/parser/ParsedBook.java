package com.highlight.parser;

import java.util.List;

/**
 * Normalized book: paragraphs separated by a blank line, chapters in reading order.
 */
public record ParsedBook(String title, String author, String language, String text, List<ParsedChapter> chapters) {
}
