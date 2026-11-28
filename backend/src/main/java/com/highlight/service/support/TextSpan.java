package com.highlight.service.support;

/**
 * Character range in the book text; {@code end} is exclusive.
 */
public record TextSpan(int start, int end) {
}
