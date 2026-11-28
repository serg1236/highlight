package com.highlight.service.support;

import com.highlight.ai.ExtractedScene;
import com.highlight.ai.ScenePresence;
import com.highlight.entity.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SceneAssemblerTest {

	private static final Long BOOK_ID = 3L;

	private static final String CHAPTER_1 = "Chapter 1\n\nAnna met Ben at the inn. They talked for hours.\n\n"
											+ "Later, at home, Anna wrote a letter. She sealed it";

	private static final String CHAPTER_2 = "Chapter 2\n\nThe letter arrived at dawn. Ben read it twice.\n\n"
											+ "In the garden Ben waited";

	private static final String TEXT = CHAPTER_1 + "\n\n" + CHAPTER_2;

	private static final int CHAPTER_2_START = CHAPTER_1.length() + 2;

	private static final List<BookChapter> CHAPTERS = List.of(
			BookChapter.builder().id(10L).bookId(BOOK_ID).chapterIndex(0).startOffset(0)
					.endOffset(CHAPTER_1.length()).build(),
			BookChapter.builder().id(11L).bookId(BOOK_ID).chapterIndex(1).startOffset(CHAPTER_2_START)
					.endOffset(TEXT.length()).build());

	private static final BookChunk CHUNK_1 = new BookChunk(0, 0, CHAPTER_1.length(), List.of());

	private static final BookChunk CHUNK_2 = new BookChunk(1, CHAPTER_2_START, TEXT.length(), List.of());

	private final SceneAssembler assembler = new SceneAssembler(new QuoteLocator());

	private List<BookCharacter> characters;

	private BookLocation inn;

	private BookLocation home;

	private MergedLocations locations;

	@BeforeEach
	void setUp() {
		characters = List.of(
				character("Anna Smith", "Anna"),
				character("Ben Brown", "Ben", "Mr. B"),
				character("Bob Brown", "Mr. B"));
		inn = BookLocation.builder().bookId(BOOK_ID).name("The inn").build();
		home = BookLocation.builder().bookId(BOOK_ID).name("Anna's home").build();
		locations = new MergedLocations(List.of(inn, home), Map.of("the inn", inn, "anna's home", home, "home", home));
	}

	@Test
	void assemble_startAndEndQuotesLocated() {
		// given
		var batches = List.of(new ChunkSceneBatch(CHUNK_1, List.of(
				scene("Anna met Ben at the inn", "They talked for hours", 1, "The inn"),
				scene("Later, at home", "She sealed it", 1, "home"))));
		var expected = List.of(
				snapshot(0, 10L, at("Anna met Ben"), end("They talked for hours"), "The inn"),
				snapshot(1, 10L, at("Later, at home"), end("She sealed it"), "Anna's home"));

		// when
		var actual = assembler.assemble(BOOK_ID, TEXT, CHAPTERS, batches, characters, locations);

		// then
		assertEquals(expected, snapshots(actual));
		assertEquals(at("Anna met Ben"), inn.getFirstOffset());
		assertEquals(at("Later, at home"), home.getFirstOffset());
	}

	@Test
	void assemble_endQuoteMissingOrPastNextStart_fallsBackToNextStartOrChunkEnd() {
		// given: the first end quote lies inside the second scene, the second is not in the text
		var batches = List.of(new ChunkSceneBatch(CHUNK_1, List.of(
				scene("Anna met Ben at the inn", "Anna wrote a letter", 1, ""),
				scene("Later, at home", "not in the text", 1, ""))));
		var expected = List.of(
				snapshot(0, 10L, at("Anna met Ben"), at("Later, at home"), null),
				snapshot(1, 10L, at("Later, at home"), CHAPTER_1.length(), null));

		// when
		var actual = assembler.assemble(BOOK_ID, TEXT, CHAPTERS, batches, characters, locations);

		// then
		assertEquals(expected, snapshots(actual));
	}

	@Test
	void assemble_startQuoteMissing_noOffsetsAndChapterFromNumber() {
		// given
		var batches = List.of(new ChunkSceneBatch(CHUNK_2, List.of(
				scene("never written", "Ben read it twice", 2, "The inn"),
				scene("In the garden", "Ben waited", 2, "Nowhere"))));
		var expected = List.of(
				snapshot(0, 11L, null, null, "The inn"),
				snapshot(1, 11L, at("In the garden"), end("Ben waited"), null));

		// when
		var actual = assembler.assemble(BOOK_ID, TEXT, CHAPTERS, batches, characters, locations);

		// then
		assertEquals(expected, snapshots(actual));
		assertEquals(null, inn.getFirstOffset());
	}

	@Test
	void assemble_continuesPrevious_extendsLastSceneOfPreviousChunk() {
		// given
		var first = new ExtractedScene("Later, at home", "She sealed it", 1, false, "Anna writes.", false,
				List.of(new ScenePresence("Anna", false)), "", "", "", TimeOfDay.NIGHT, "", false, "");
		var continuation = new ExtractedScene("The letter arrived", "arrived at dawn", 2, true, "It arrives.", true,
				List.of(new ScenePresence("anna", true), new ScenePresence("Ben", false)), "home", "", "a quiet room",
				TimeOfDay.DAWN, "", false, "");
		var batches = List.of(
				new ChunkSceneBatch(CHUNK_1, List.of(first)),
				new ChunkSceneBatch(CHUNK_2, List.of(continuation,
						scene("In the garden", "Ben waited", 2, ""))));
		var expected = List.of(
				new SceneSnapshot(0, 10L, at("Later, at home"), end("arrived at dawn"), "Anna writes. It arrives.",
						true, "Anna's home", "a quiet room", TimeOfDay.NIGHT, null, false, null,
						List.of("Anna Smith:true", "Ben Brown:false")),
				snapshot(1, 11L, at("In the garden"), end("Ben waited"), null));

		// when
		var actual = assembler.assemble(BOOK_ID, TEXT, CHAPTERS, batches, characters, locations);

		// then
		assertEquals(expected, snapshots(actual));
	}

	@Test
	void assemble_firstSceneContinuesPreviousWithoutEarlierScene_keptAsOwnScene() {
		// given
		var continuation = new ExtractedScene("The letter arrived", "Ben read it twice", 2, true, "It arrives.",
				false, List.of(), "", "", "", null, "", false, "");
		var batches = List.of(new ChunkSceneBatch(CHUNK_2, List.of(continuation)));
		var expected = List.of(new SceneSnapshot(0, 11L, at("The letter arrived"), end("Ben read it twice"),
				"It arrives.", false, null, null, TimeOfDay.UNKNOWN, null, false, null, List.of()));

		// when
		var actual = assembler.assemble(BOOK_ID, TEXT, CHAPTERS, batches, characters, locations);

		// then
		assertEquals(expected, snapshots(actual));
	}

	@Test
	void assemble_characters_resolvedByNameOrAliasAndUnresolvedSkipped() {
		// given: "Mr. B" is shared by two characters, "Carl" is unknown, Ben appears twice
		var extracted = new ExtractedScene("Anna met Ben at the inn", "They talked for hours", 1, false,
				" They talk. ", true, List.of(
				new ScenePresence(" ANNA SMITH ", false),
				new ScenePresence("Mr. B", true),
				new ScenePresence("Carl", true),
				new ScenePresence(" ", true),
				new ScenePresence("Ben", false),
				new ScenePresence("Ben Brown", true)),
				"The inn", "", " the inn's parlour ", TimeOfDay.EVENING, " that night ", true, " a memory ");
		var batches = List.of(new ChunkSceneBatch(CHUNK_1, List.of(extracted)));
		var expected = List.of(new SceneSnapshot(0, 10L, at("Anna met Ben"), end("They talked for hours"),
				"They talk.", true, "The inn", "the inn's parlour", TimeOfDay.EVENING, "that night", true,
				"a memory", List.of("Anna Smith:false", "Ben Brown:true")));

		// when
		var actual = assembler.assemble(BOOK_ID, TEXT, CHAPTERS, batches, characters, locations);

		// then
		assertEquals(expected, snapshots(actual));
	}

	private static BookCharacter character(String name, String... aliases) {
		var character = BookCharacter.builder().bookId(BOOK_ID).canonicalName(name).build();
		for (var alias : aliases) {
			character.addAlias(CharacterAlias.builder().alias(alias).build());
		}
		return character;
	}

	private static ExtractedScene scene(String startQuote, String endQuote, int chapter, String location) {
		return new ExtractedScene(startQuote, endQuote, chapter, false, "", false, List.of(), location, "", "",
				TimeOfDay.DAY, "", false, "");
	}

	private static int at(String quote) {
		return TEXT.indexOf(quote);
	}

	private static int end(String quote) {
		return TEXT.indexOf(quote) + quote.length();
	}

	private static SceneSnapshot snapshot(int index, Long chapterId, Integer start, Integer end, String location) {
		return new SceneSnapshot(index, chapterId, start, end, "", false, location, null, TimeOfDay.DAY, null, false,
				null, List.of());
	}

	private static List<SceneSnapshot> snapshots(List<BookScene> scenes) {
		return scenes.stream()
				.map(scene -> new SceneSnapshot(scene.getSceneIndex(), scene.getChapterId(), scene.getStartOffset(),
						scene.getEndOffset(), scene.getSummary(), scene.isHasDialog(),
						scene.getLocation() == null ? null : scene.getLocation().getName(), scene.getLocationQuote(),
						scene.getTimeOfDay(), scene.getTimeMarker(), scene.isTimelineShift(), scene.getTimelineNote(),
						scene.getCharacters().stream()
								.map(c -> c.getCharacter().getCanonicalName() + ":" + c.isSpeaks())
								.toList()))
				.toList();
	}

	record SceneSnapshot(int sceneIndex, Long chapterId, Integer startOffset, Integer endOffset, String summary,
						 boolean hasDialog, String location, String locationQuote, TimeOfDay timeOfDay,
						 String timeMarker,
						 boolean timelineShift, String timelineNote, List<String> characters) {
	}
}
