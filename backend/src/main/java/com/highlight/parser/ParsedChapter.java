package com.highlight.parser;

/**
 * Chapter boundaries inside {@link ParsedBook#text()}; {@code endOffset} is exclusive.
 */
public record ParsedChapter(int index, String title, int startOffset, int endOffset) {
}
