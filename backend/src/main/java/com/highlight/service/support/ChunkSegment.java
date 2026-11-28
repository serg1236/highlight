package com.highlight.service.support;

/**
 * Part of one chapter that belongs to a chunk; {@code continuation} is true when the chapter started in an
 * earlier segment.
 */
public record ChunkSegment(int chapterIndex, String chapterTitle, int startOffset, int endOffset,
						   boolean continuation) {

	int length() {
		return endOffset - startOffset;
	}
}
