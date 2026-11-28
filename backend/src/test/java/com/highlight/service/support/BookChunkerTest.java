package com.highlight.service.support;

import com.highlight.entity.BookChapter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BookChunkerTest {

	private final BookChunker chunker = new BookChunker();

	@Test
	void split_smallBook_singleChunk() {
		// given
		var text = "One two.\n\nThree four.";
		var chapters = List.of(chapter(0, "C1", 0, 8), chapter(1, "C2", 10, 21));
		var expected = List.of(new BookChunk(0, 0, 21, List.of(
				new ChunkSegment(0, "C1", 0, 8, false),
				new ChunkSegment(1, "C2", 10, 21, false))));

		// when
		var actual = chunker.split(text, chapters, 100);

		// then
		assertEquals(expected, actual);
	}

	@Test
	void split_chaptersOverBudget_packedIntoSeparateChunks() {
		// given
		var text = "One two.\n\nThree four.";
		var chapters = List.of(chapter(0, "C1", 0, 8), chapter(1, "C2", 10, 21));
		var expected = List.of(
				new BookChunk(0, 0, 8, List.of(new ChunkSegment(0, "C1", 0, 8, false))),
				new BookChunk(1, 10, 21, List.of(new ChunkSegment(1, "C2", 10, 21, false))));

		// when
		var actual = chunker.split(text, chapters, 3);

		// then
		assertEquals(expected, actual);
	}

	@Test
	void split_oversizedChapter_splitsAtParagraphBreak() {
		// given
		var text = "Alpha beta.\n\nGamma delta.\n\nEpsilon.";
		var chapters = List.of(chapter(0, "Long", 0, text.length()));
		var expected = List.of(
				new BookChunk(0, 0, 25, List.of(new ChunkSegment(0, "Long", 0, 25, false))),
				new BookChunk(1, 27, 35, List.of(new ChunkSegment(0, "Long", 27, 35, true))));

		// when
		var actual = chunker.split(text, chapters, 7);

		// then
		assertEquals(expected, actual);
	}

	@Test
	void split_oversizedParagraph_splitsAtSpaces() {
		// given
		var text = "aaaa bbbb cccc";
		var chapters = List.of(chapter(0, null, 0, text.length()));
		var expected = List.of(
				new BookChunk(0, 0, 9, List.of(
						new ChunkSegment(0, null, 0, 4, false),
						new ChunkSegment(0, null, 5, 9, true))),
				new BookChunk(1, 10, 14, List.of(new ChunkSegment(0, null, 10, 14, true))));

		// when
		var actual = chunker.split(text, chapters, 2);

		// then
		assertEquals(expected, actual);
	}

	@Test
	void split_wordLongerThanBudget_hardSplit() {
		// given
		var text = "abcdefghij";
		var chapters = List.of(chapter(0, null, 0, text.length()));
		var expected = List.of(
				new BookChunk(0, 0, 4, List.of(new ChunkSegment(0, null, 0, 4, false))),
				new BookChunk(1, 4, 8, List.of(new ChunkSegment(0, null, 4, 8, true))),
				new BookChunk(2, 8, 10, List.of(new ChunkSegment(0, null, 8, 10, true))));

		// when
		var actual = chunker.split(text, chapters, 1);

		// then
		assertEquals(expected, actual);
	}

	@Test
	void split_cyrillicText_usesSmallerCharacterBudget() {
		// given: 10 Cyrillic letters per chapter; budget 5 tokens = 12 chars for Cyrillic, 20 for Latin
		var text = "абвгдеєжзи\n\nклмнопрсту";
		var chapters = List.of(chapter(0, null, 0, 10), chapter(1, null, 12, 22));
		var expected = List.of(
				new BookChunk(0, 0, 10, List.of(new ChunkSegment(0, null, 0, 10, false))),
				new BookChunk(1, 12, 22, List.of(new ChunkSegment(1, null, 12, 22, false))));

		// when
		var actual = chunker.split(text, chapters, 5);

		// then
		assertEquals(expected, actual);
	}

	@Test
	void estimateTokens_latinText() {
		assertEquals(10, chunker.estimateTokens("abcd".repeat(10)));
	}

	@Test
	void estimateTokens_cyrillicText() {
		assertEquals(20, chunker.estimateTokens("абвгд".repeat(10)));
	}

	@Test
	void estimateTokens_emptyText() {
		assertEquals(0, chunker.estimateTokens(""));
	}

	@Test
	void promptText_rendersChapterMarkers() {
		// given
		var text = "Intro text.\n\nMore text.";
		var chunk = new BookChunk(0, 0, 23, List.of(
				new ChunkSegment(0, "Opening", 0, 11, false),
				new ChunkSegment(1, null, 13, 23, true)));
		var expected = "[[CHAPTER 1: Opening]]\nIntro text.\n\n[[CHAPTER 2: untitled (continued)]]\nMore text.\n\n";

		// when
		var actual = chunk.promptText(text);

		// then
		assertEquals(expected, actual);
	}

	private static BookChapter chapter(int index, String title, int start, int end) {
		return BookChapter.builder().chapterIndex(index).title(title).startOffset(start).endOffset(end).build();
	}
}
