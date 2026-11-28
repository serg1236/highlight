package com.highlight.service.support;

import com.highlight.ai.ExtractedSettingClue;
import com.highlight.ai.WorldSynthesis;
import com.highlight.entity.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SettingAssemblerTest {

	private static final Long BOOK_ID = 2L;

	private static final String TEXT = "Chapter 1\n\nThe carriage was ordered at noon.\n\nChapter 2\n\nCandles burned.";

	private static final int CHAPTER_2_START = TEXT.indexOf("Chapter 2");

	private static final List<BookChapter> CHAPTERS = List.of(
			BookChapter.builder().id(20L).bookId(BOOK_ID).chapterIndex(0).startOffset(0).endOffset(CHAPTER_2_START)
					.build(),
			BookChapter.builder().id(21L).bookId(BOOK_ID).chapterIndex(1).startOffset(CHAPTER_2_START)
					.endOffset(TEXT.length()).build());

	private static final BookChunk CHUNK = new BookChunk(0, 0, TEXT.length(), List.of());

	private final SettingAssembler assembler = new SettingAssembler(new QuoteLocator());

	@Test
	void toClues_locatesQuotesAndDropsIncompleteClues() {
		// given
		var clues = List.of(
				new ExtractedSettingClue(SettingClueCategory.TECHNOLOGY, " travel by carriage ",
						"The carriage was ordered", 1),
				new ExtractedSettingClue(SettingClueCategory.TECHNOLOGY, "candlelight", "gas lamps glowed", 2),
				new ExtractedSettingClue(null, "no category", "Candles burned", 2),
				new ExtractedSettingClue(SettingClueCategory.CUSTOM, " ", "Candles burned", 2),
				new ExtractedSettingClue(SettingClueCategory.CUSTOM, "no quote", null, 2));
		var carriage = TEXT.indexOf("The carriage was ordered");
		var expected = List.of(
				new ClueSnapshot(BOOK_ID, SettingClueCategory.TECHNOLOGY, "travel by carriage",
						"The carriage was ordered", carriage, carriage + 24, 20L),
				new ClueSnapshot(BOOK_ID, SettingClueCategory.TECHNOLOGY, "candlelight", "gas lamps glowed", null,
						null, 21L));

		// when
		var actual = assembler.toClues(BOOK_ID, TEXT, CHAPTERS, CHUNK, clues);

		// then
		assertEquals(expected, actual.stream().map(SettingAssemblerTest::snapshot).toList());
	}

	@Test
	void toSetting_copiesFieldsAndNormalizesBlanks() {
		// given
		var synthesis = new WorldSynthesis(Realism.FANTASY, " high fantasy ", "", null, "the Northern Kingdoms",
				"swords and sorcery", "furs and cloaks", "stone keeps", " ", " Stone keeps, fur cloaks ", List.of());
		var expected = new SettingSnapshot(BOOK_ID, Realism.FANTASY, "high fantasy", null, EvidenceSource.INFERRED,
				"the Northern Kingdoms", "swords and sorcery", "furs and cloaks", "stone keeps", null,
				"Stone keeps, fur cloaks");

		// when
		var actual = assembler.toSetting(BOOK_ID, synthesis);

		// then
		assertEquals(expected, snapshot(actual));
	}

	@Test
	void toSetting_missingPromptLine_empty() {
		// given
		var synthesis = new WorldSynthesis(Realism.REALISTIC, null, "1990s", EvidenceSource.CITED, null, null,
				null, null, null, null, List.of());
		var expected = new SettingSnapshot(BOOK_ID, Realism.REALISTIC, null, "1990s", EvidenceSource.CITED, null,
				null, null, null, null, "");

		// when
		var actual = assembler.toSetting(BOOK_ID, synthesis);

		// then
		assertEquals(expected, snapshot(actual));
	}

	private static ClueSnapshot snapshot(SettingClue clue) {
		return new ClueSnapshot(clue.getBookId(), clue.getCategory(), clue.getDescription(), clue.getQuote(),
				clue.getStartOffset(), clue.getEndOffset(), clue.getChapterId());
	}

	private static SettingSnapshot snapshot(BookSetting setting) {
		return new SettingSnapshot(setting.getBookId(), setting.getRealism(), setting.getGenre(), setting.getPeriod(),
				setting.getPeriodSource(), setting.getRegion(), setting.getTechnology(), setting.getClothingNorms(),
				setting.getArchitecture(), setting.getVisualMood(), setting.getPromptLine());
	}

	record ClueSnapshot(Long bookId, SettingClueCategory category, String description, String quote,
						Integer startOffset, Integer endOffset, Long chapterId) {
	}

	record SettingSnapshot(Long bookId, Realism realism, String genre, String period, EvidenceSource periodSource,
						   String region, String technology, String clothingNorms, String architecture,
						   String visualMood,
						   String promptLine) {
	}
}
