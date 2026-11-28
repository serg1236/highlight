package com.highlight.service.support;

import com.highlight.ai.ExtractedSettingClue;
import com.highlight.ai.WorldSynthesis;
import com.highlight.entity.BookChapter;
import com.highlight.entity.BookSetting;
import com.highlight.entity.EvidenceSource;
import com.highlight.entity.SettingClue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * Builds setting clues with offsets located in the book text, and the book setting from the world synthesis.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SettingAssembler {

	private final QuoteLocator quoteLocator;

	/**
	 * Clues without a category, description or quote are dropped; a clue whose quote cannot be found keeps no
	 * offsets.
	 */
	public List<SettingClue> toClues(Long bookId, String text, List<BookChapter> chapters, BookChunk chunk,
									 List<ExtractedSettingClue> clues) {
		return clues.stream()
				.filter(clue -> clue.category() != null && !isBlank(clue.description()) && !isBlank(clue.quote()))
				.map(clue -> toClue(bookId, text, chapters, chunk, clue))
				.toList();
	}

	public BookSetting toSetting(Long bookId, WorldSynthesis synthesis) {
		return BookSetting.builder()
				.bookId(bookId)
				.realism(synthesis.realism())
				.genre(blankToNull(synthesis.genre()))
				.period(blankToNull(synthesis.period()))
				.periodSource(Objects.requireNonNullElse(synthesis.periodSource(), EvidenceSource.INFERRED))
				.region(blankToNull(synthesis.region()))
				.technology(blankToNull(synthesis.technology()))
				.clothingNorms(blankToNull(synthesis.clothingNorms()))
				.architecture(blankToNull(synthesis.architecture()))
				.visualMood(blankToNull(synthesis.visualMood()))
				.promptLine(synthesis.promptLine() == null ? "" : synthesis.promptLine().strip())
				.build();
	}

	private SettingClue toClue(Long bookId, String text, List<BookChapter> chapters, BookChunk chunk,
							   ExtractedSettingClue clue) {
		var span = quoteLocator.locate(text, chunk.startOffset(), chunk.endOffset(), clue.quote());
		if (span.isEmpty()) {
			log.warn("Setting clue quote not found in text: bookId={}, chunk={}, category={}, quote={}", bookId,
					chunk.index(), clue.category(), clue.quote());
		}
		return SettingClue.builder()
				.bookId(bookId)
				.category(clue.category())
				.description(clue.description().strip())
				.quote(clue.quote())
				.startOffset(span.map(TextSpan::start).orElse(null))
				.endOffset(span.map(TextSpan::end).orElse(null))
				.chapterId(ChapterLookup.chapterId(chapters, span, clue.chapter()))
				.build();
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	private static String blankToNull(String value) {
		return isBlank(value) ? null : value.strip();
	}
}
