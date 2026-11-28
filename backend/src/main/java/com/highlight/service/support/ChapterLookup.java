package com.highlight.service.support;

import com.highlight.entity.BookChapter;

import java.util.List;
import java.util.Optional;

/**
 * Resolves the chapter of a quote: by its located offset when there is one, otherwise by the 1-based chapter
 * number the model reported.
 */
public final class ChapterLookup {

	private ChapterLookup() {
	}

	public static Long chapterId(List<BookChapter> chapters, Optional<TextSpan> span, int chapterNumber) {
		return span.map(found -> chapterAt(chapters, found.start()))
				.orElseGet(() -> chapterByNumber(chapters, chapterNumber));
	}

	static Long chapterAt(List<BookChapter> chapters, int offset) {
		return chapters.stream()
				.filter(chapter -> chapter.getStartOffset() <= offset && offset < chapter.getEndOffset())
				.map(BookChapter::getId)
				.findFirst()
				.orElse(null);
	}

	private static Long chapterByNumber(List<BookChapter> chapters, int chapterNumber) {
		return chapters.stream()
				.filter(chapter -> chapter.getChapterIndex() == chapterNumber - 1)
				.map(BookChapter::getId)
				.findFirst()
				.orElse(null);
	}
}
