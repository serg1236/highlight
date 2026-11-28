package com.highlight.service.support;

import java.util.List;

/**
 * Contiguous range of book text sent to the model in one extraction call.
 */
public record BookChunk(int index, int startOffset, int endOffset, List<ChunkSegment> segments) {

	/**
	 * Renders the chunk with {@code [[CHAPTER n: title]]} markers so the model can report chapter numbers.
	 * Chapter numbers are 1-based.
	 */
	public String promptText(String bookText) {
		var prompt = new StringBuilder();
		for (var segment : segments) {
			prompt.append("[[CHAPTER ")
					.append(segment.chapterIndex() + 1)
					.append(": ")
					.append(segment.chapterTitle() == null ? "untitled" : segment.chapterTitle())
					.append(segment.continuation() ? " (continued)" : "")
					.append("]]\n")
					.append(bookText, segment.startOffset(), segment.endOffset())
					.append("\n\n");
		}
		return prompt.toString();
	}
}
