package com.highlight.service.impl;

import com.highlight.ai.*;
import com.highlight.ai.WorldSynthesisInput.ClueSummary;
import com.highlight.ai.WorldSynthesisInput.LocationSummary;
import com.highlight.config.AnthropicProperties;
import com.highlight.config.ExtractionProperties;
import com.highlight.entity.*;
import com.highlight.repository.*;
import com.highlight.service.support.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookExtractionServiceImplTest {

	private static final Long BOOK_ID = 1L;

	private static final String CHAPTER_1 = "Chapter 1\n\nAnna had red hair.";

	private static final String CHAPTER_2 = "Chapter 2\n\nAnn wore a green coat.";

	private static final String CHAPTER_3 = "Chapter 3\n\nAnnie smiled widely.";

	private static final String TEXT = CHAPTER_1 + "\n\n" + CHAPTER_2 + "\n\n" + CHAPTER_3;

	/**
	 * 9 tokens = 36 Latin characters, so every chapter becomes its own chunk.
	 */
	private static final int CHUNK_MAX_TOKENS = 9;

	/**
	 * Enough tokens for the whole text in one chunk.
	 */
	private static final int WHOLE_TEXT_TOKENS = 10_000;

	/**
	 * More than the three scene chunks of {@link #threeChapters()}, so they all run in one wave.
	 */
	private static final int SCENE_PARALLELISM = 4;

	private static final ChunkScenes NO_SCENES = new ChunkScenes(List.of(), List.of());

	private static final WorldSynthesisInput EMPTY_WORLD_INPUT = new WorldSynthesisInput("Sample", null, List.of(),
			List.of());

	@Mock
	private BookRepository bookRepository;

	@Mock
	private BookContentRepository contentRepository;

	@Mock
	private BookChapterRepository chapterRepository;

	@Mock
	private BookCharacterRepository characterRepository;

	@Mock
	private BookSceneRepository sceneRepository;

	@Mock
	private BookLocationRepository locationRepository;

	@Mock
	private BookSettingRepository settingRepository;

	@Mock
	private SettingClueRepository clueRepository;

	@Mock
	private CharacterExtractionClient extractionClient;

	@Mock
	private SceneExtractionClient sceneClient;

	@Captor
	private ArgumentCaptor<List<BookCharacter>> charactersCaptor;

	@Captor
	private ArgumentCaptor<List<BookLocation>> locationsCaptor;

	@Captor
	private ArgumentCaptor<List<BookScene>> scenesCaptor;

	@Captor
	private ArgumentCaptor<List<SettingClue>> cluesCaptor;

	@Captor
	private ArgumentCaptor<BookSetting> settingCaptor;

	private BookExtractionServiceImpl service;

	private Book book;

	@BeforeEach
	void setUp() {
		var pricing = new AnthropicProperties.Pricing(new BigDecimal("0.10"), new BigDecimal("0.50"), 100_000,
				new BigDecimal("0.50"), new BigDecimal("2.50"));
		service = service(pricing, CHUNK_MAX_TOKENS, CHUNK_MAX_TOKENS, SCENE_PARALLELISM);
		book = Book.builder().id(BOOK_ID).title("Sample").originalFilename("sample.txt").format(BookFormat.TXT)
				.status(BookStatus.UPLOADED).charCount(TEXT.length()).build();
	}

	@Test
	void extract_multipleChunks_storesCharactersScenesLocationsAndSetting() {
		// given
		givenStoredBook(threeChapters());
		var anna = new ExtractedCharacter("Anna", List.of("Ann"), CharacterSex.FEMALE,
				List.of(observation(ObservationKind.APPEARANCE,
						ObservationCategory.HAIR, "red hair", "Anna had red hair", 1)));
		var ann = new ExtractedCharacter("Ann", List.of("Annie", "ann", " "), null, List.of(
				observation(ObservationKind.CLOTHING, ObservationCategory.OUTERWEAR, "green coat", "wore a green coat",
						2),
				observation(ObservationKind.CLOTHING, ObservationCategory.HEADWEAR, "blue hat", "a blue hat", 2)));
		var annaRoster = List.of(new RosterEntry("Anna", List.of("Ann")));
		var fullRoster = List.of(new RosterEntry("Anna", List.of("Ann", "Annie")));
		when(extractionClient.extractCharacters(prompt(1, CHAPTER_1), List.of()))
				.thenReturn(response(List.of(anna), 1000, 200));
		when(extractionClient.extractCharacters(prompt(2, CHAPTER_2), annaRoster))
				.thenReturn(response(List.of(ann), 1100, 300));
		when(extractionClient.extractCharacters(prompt(3, CHAPTER_3), fullRoster))
				.thenReturn(response(List.of(), 900, 100));
		when(sceneClient.extractScenes(prompt(1, CHAPTER_1), fullRoster, List.of())).thenReturn(scenes(
				new ChunkScenes(List.of(scene("Anna had red hair", 1, "Kitchen", "warm kitchen", "Anna", false)),
						List.of(new ExtractedSettingClue(SettingClueCategory.TECHNOLOGY, "gas lamps", "red hair", 1)))));
		when(sceneClient.extractScenes(prompt(2, CHAPTER_2), fullRoster, List.of())).thenReturn(scenes(
				new ChunkScenes(List.of(scene("Ann wore a green coat", 2, "kitchen", "small kitchen", "Ann", true)),
						List.of())));
		when(sceneClient.extractScenes(prompt(3, CHAPTER_3), fullRoster, List.of()))
				.thenReturn(scenes(NO_SCENES));
		when(extractionClient.mergeCharacters(List.of(
				new MergeCandidate(0, "Anna", List.of("Ann"), CharacterSex.FEMALE, List.of("red hair")),
				new MergeCandidate(1, "Ann", List.of("Annie"), CharacterSex.UNDEFINED,
						List.of("green coat", "blue hat")))))
				.thenReturn(new ExtractionResponse<>(new MergeDecision(List.of(new MergeCluster("Anna Smith",
						List.of(0, 1)))), new TokenUsage(500, 50)));
		var worldInput = new WorldSynthesisInput("Sample", null,
				List.of(new ClueSummary(SettingClueCategory.TECHNOLOGY, "gas lamps", "red hair")),
				List.of(new LocationSummary("Kitchen", List.of("warm kitchen", "small kitchen"))));
		when(sceneClient.synthesizeWorld(worldInput)).thenReturn(new ExtractionResponse<>(world(
				List.of(new SynthesizedLocation("Kitchen", List.of("Kitchen"), "Victorian kitchen"))),
				new TokenUsage(300, 100)));

		var hairStart = TEXT.indexOf("Anna had red hair");
		var coatStart = TEXT.indexOf("wore a green coat");
		var annStart = TEXT.indexOf("Ann wore a green coat");
		var expectedCharacters = List.of(new CharacterSnapshot("Anna Smith", CharacterSex.FEMALE, hairStart,
				List.of("Anna", "Ann", "Annie"), List.of(
				new LocatedObservation(ObservationKind.APPEARANCE, ObservationCategory.HAIR, "red hair",
						"Anna had red hair", hairStart, hairStart + 17, 10L, false, false),
				new LocatedObservation(ObservationKind.CLOTHING, ObservationCategory.OUTERWEAR, "green coat",
						"wore a green coat", coatStart, coatStart + 17, 11L, false, false),
				new LocatedObservation(ObservationKind.CLOTHING, ObservationCategory.HEADWEAR, "blue hat",
						"a blue hat", null, null, 11L, false, false))));
		var expectedLocations = List.of(new LocationSnapshot("Kitchen", "Victorian kitchen", hairStart));
		var expectedScenes = List.of(
				new SceneSnapshot(0, 10L, hairStart, hairStart + 17, "Kitchen", List.of("Anna Smith:false")),
				new SceneSnapshot(1, 11L, annStart, annStart + 21, "Kitchen", List.of("Anna Smith:true")));
		var redHairStart = TEXT.indexOf("red hair");
		var expectedClues = List.of(new ClueSnapshot(SettingClueCategory.TECHNOLOGY, "gas lamps", redHairStart,
				10L));
		// characters 0.000200 + 0.000260 + 0.000140, scenes 3 x 0.000150, merge 0.000075, world 0.000080
		var expectedBook = new BookSnapshot(BookStatus.READY, 6, 6, 6800, 1050, new BigDecimal("0.001205"), null,
				"en");

		// when
		service.extract(BOOK_ID);

		// then
		verify(characterRepository).saveAll(charactersCaptor.capture());
		verify(locationRepository).saveAll(locationsCaptor.capture());
		verify(sceneRepository).saveAll(scenesCaptor.capture());
		verify(clueRepository).saveAll(cluesCaptor.capture());
		verify(settingRepository).save(settingCaptor.capture());
		assertEquals(expectedCharacters, snapshots(charactersCaptor.getValue()));
		assertEquals(expectedLocations, locationSnapshots(locationsCaptor.getValue()));
		assertEquals(expectedScenes, sceneSnapshots(scenesCaptor.getValue()));
		assertEquals(expectedClues, clueSnapshots(cluesCaptor.getValue()));
		assertEquals(new SettingSnapshot(BOOK_ID, Realism.REALISTIC, "c. 1880", EvidenceSource.INFERRED,
				"Victorian England, gas lamps"), settingSnapshot(settingCaptor.getValue()));
		assertEquals(expectedBook, snapshot(book));
	}

	@Test
	void extract_singleChunk_skipsMergeCallButSynthesizesWorld() {
		// given
		var chapters = List.of(chapter(10L, 0, 0, TEXT.length()));
		givenStoredBook(chapters);
		var service = serviceWithChunkTokens(WHOLE_TEXT_TOKENS, WHOLE_TEXT_TOKENS);
		var anna = new ExtractedCharacter("Anna", List.of(), CharacterSex.MALE, List.of());
		when(extractionClient.extractCharacters(prompt(1, TEXT), List.of())).thenReturn(response(List.of(anna), 10, 5));
		when(sceneClient.extractScenes(prompt(1, TEXT), List.of(new RosterEntry("Anna", List.of())), List.of()))
				.thenReturn(scenes(NO_SCENES));
		givenEmptyWorld();
		var expectedCharacters = List.of(new CharacterSnapshot("Anna", CharacterSex.MALE, null, List.of(), List.of()));

		// when
		service.extract(BOOK_ID);

		// then
		verify(extractionClient, never()).mergeCharacters(anyList());
		verify(sceneClient).synthesizeWorld(EMPTY_WORLD_INPUT);
		verify(characterRepository).saveAll(charactersCaptor.capture());
		assertEquals(expectedCharacters, snapshots(charactersCaptor.getValue()));
		assertEquals(BookStatus.READY, book.getStatus());
	}

	@ParameterizedTest
	@CsvSource(nullValues = "null", value = {
			"null, en, en, en, en",
			"fr, en, en, en, en",
			"en-US, en, en, en, en-US",
			"English, en, en, en, English",
			"en, en, uk, uk, uk",
			"null, en, uk, null, en",
			"de, ' ', null, ' ', de",
			"null, ' EN ', en, uk, en"})
	void extract_detectedLanguage_correctsBookLanguage(String metadataLanguage, String chunk1Language,
													   String chunk2Language, String chunk3Language, String expectedLanguage) {
		// given
		book.setLanguage(metadataLanguage);
		givenStoredBook(threeChapters());
		givenNoScenes();
		givenEmptyWorld();
		when(extractionClient.extractCharacters(prompt(1, CHAPTER_1), List.of()))
				.thenReturn(response(chunk1Language, List.of(), 10, 5));
		when(extractionClient.extractCharacters(prompt(2, CHAPTER_2), List.of()))
				.thenReturn(response(chunk2Language, List.of(), 10, 5));
		when(extractionClient.extractCharacters(prompt(3, CHAPTER_3), List.of()))
				.thenReturn(response(chunk3Language, List.of(), 10, 5));

		// when
		service.extract(BOOK_ID);

		// then
		assertEquals(BookStatus.READY, book.getStatus());
		assertEquals(expectedLanguage, book.getLanguage());
	}

	@Test
	void extract_characterCallFails_marksBookFailed() {
		// given
		givenStoredBook(threeChapters());
		when(extractionClient.extractCharacters(prompt(1, CHAPTER_1), List.of()))
				.thenThrow(new ExtractionException("Model stopped with refusal"));
		var expectedBook = new BookSnapshot(BookStatus.FAILED, 6, 0, 0, 0, BigDecimal.ZERO,
				"ExtractionException: Model stopped with refusal", null);

		// when
		service.extract(BOOK_ID);

		// then
		verify(characterRepository, never()).saveAll(anyList());
		verify(sceneRepository, never()).saveAll(anyList());
		assertEquals(expectedBook, snapshot(book));
	}

	@Test
	void extract_sceneCallFails_marksBookFailed() {
		// given
		givenStoredBook(threeChapters());
		var service = serviceWithChunkTokens(CHUNK_MAX_TOKENS, CHUNK_MAX_TOKENS);
		givenNoCharacters();
		when(sceneClient.extractScenes(prompt(1, CHAPTER_1), List.of(), List.of()))
				.thenThrow(new ExtractionException("Model stopped with max_tokens"));
		var expectedBook = new BookSnapshot(BookStatus.FAILED, 6, 3, 30, 15, new BigDecimal("0.000045"),
				"ExtractionException: Model stopped with max_tokens", null);

		// when
		service.extract(BOOK_ID);

		// then
		verify(characterRepository, never()).saveAll(anyList());
		verify(sceneRepository, never()).saveAll(anyList());
		verify(sceneClient, never()).synthesizeWorld(any());
		assertEquals(expectedBook, snapshot(book));
	}

	@Test
	void extract_smallerSceneChunksInWaves_laterWaveSeesEarlierLocations() {
		// given
		givenStoredBook(threeChapters());
		var service = serviceWithChunkTokens(WHOLE_TEXT_TOKENS, CHUNK_MAX_TOKENS, 2);
		var anna = new ExtractedCharacter("Anna", List.of("Ann", "Annie"), CharacterSex.FEMALE, List.of());
		var roster = List.of(new RosterEntry("Anna", List.of("Ann", "Annie")));
		var wholeTextPrompt = prompt(1, CHAPTER_1) + prompt(2, CHAPTER_2) + prompt(3, CHAPTER_3);
		when(extractionClient.extractCharacters(wholeTextPrompt, List.of())).thenReturn(response(List.of(anna), 10, 5));
		when(sceneClient.extractScenes(prompt(1, CHAPTER_1), roster, List.of())).thenReturn(scenes(
				new ChunkScenes(List.of(scene("Anna had red hair", 1, "Kitchen", "", "Anna", false)), List.of())));
		when(sceneClient.extractScenes(prompt(2, CHAPTER_2), roster, List.of())).thenReturn(scenes(
				new ChunkScenes(List.of(scene("Ann wore a green coat", 2, "Garden", "", "Ann", true)), List.of())));
		when(sceneClient.extractScenes(prompt(3, CHAPTER_3), roster, List.of("Kitchen", "Garden"))).thenReturn(
				scenes(new ChunkScenes(List.of(scene("Annie smiled widely", 3, "Kitchen", "", "Annie", false)),
						List.of())));
		var worldInput = new WorldSynthesisInput("Sample", null, List.of(),
				List.of(new LocationSummary("Kitchen", List.of()), new LocationSummary("Garden", List.of())));
		when(sceneClient.synthesizeWorld(worldInput)).thenReturn(new ExtractionResponse<>(world(List.of(
				new SynthesizedLocation("Kitchen", List.of("Kitchen"), "kitchen"),
				new SynthesizedLocation("Garden", List.of("Garden"), "garden"))), new TokenUsage(10, 5)));
		var hairStart = TEXT.indexOf("Anna had red hair");
		var annStart = TEXT.indexOf("Ann wore a green coat");
		var smileStart = TEXT.indexOf("Annie smiled widely");
		var expectedScenes = List.of(
				new SceneSnapshot(0, 10L, hairStart, hairStart + 17, "Kitchen", List.of("Anna:false")),
				new SceneSnapshot(1, 11L, annStart, annStart + 21, "Garden", List.of("Anna:true")),
				new SceneSnapshot(2, 12L, smileStart, smileStart + 19, "Kitchen", List.of("Anna:false")));

		// when
		service.extract(BOOK_ID);

		// then
		verify(extractionClient, never()).mergeCharacters(anyList());
		verify(sceneRepository).saveAll(scenesCaptor.capture());
		assertEquals(expectedScenes, sceneSnapshots(scenesCaptor.getValue()));
		assertEquals(BookStatus.READY, book.getStatus());
		assertEquals(4, book.getChunksTotal());
		assertEquals(4, book.getChunksDone());
	}

	@Test
	void extract_sceneCallsInOneWave_runConcurrentlyAndApplyInChunkOrder() {
		// given
		givenStoredBook(threeChapters());
		givenNoCharacters();
		var secondCallStarted = new CountDownLatch(1);
		when(sceneClient.extractScenes(prompt(1, CHAPTER_1), List.of(), List.of())).thenAnswer(invocation -> {
			if (!secondCallStarted.await(5, TimeUnit.SECONDS)) {
				throw new IllegalStateException("Chunk 2 was not called while chunk 1 was running");
			}
			return scenes(new ChunkScenes(List.of(scene("Anna had red hair", 1, "Kitchen", "", "Anna", false)),
					List.of()));
		});
		when(sceneClient.extractScenes(prompt(2, CHAPTER_2), List.of(), List.of())).thenAnswer(invocation -> {
			secondCallStarted.countDown();
			return scenes(new ChunkScenes(List.of(scene("Ann wore a green coat", 2, "Garden", "", "Ann", true)),
					List.of()));
		});
		when(sceneClient.extractScenes(prompt(3, CHAPTER_3), List.of(), List.of())).thenReturn(scenes(NO_SCENES));
		var worldInput = new WorldSynthesisInput("Sample", null, List.of(),
				List.of(new LocationSummary("Kitchen", List.of()), new LocationSummary("Garden", List.of())));
		when(sceneClient.synthesizeWorld(worldInput)).thenReturn(new ExtractionResponse<>(world(List.of(
				new SynthesizedLocation("Kitchen", List.of("Kitchen"), "kitchen"),
				new SynthesizedLocation("Garden", List.of("Garden"), "garden"))), new TokenUsage(10, 5)));
		var hairStart = TEXT.indexOf("Anna had red hair");
		var annStart = TEXT.indexOf("Ann wore a green coat");
		var expectedScenes = List.of(
				new SceneSnapshot(0, 10L, hairStart, hairStart + 17, "Kitchen", List.of()),
				new SceneSnapshot(1, 11L, annStart, annStart + 21, "Garden", List.of()));

		// when
		service.extract(BOOK_ID);

		// then
		verify(sceneRepository).saveAll(scenesCaptor.capture());
		assertEquals(expectedScenes, sceneSnapshots(scenesCaptor.getValue()));
		assertEquals(BookStatus.READY, book.getStatus());
		assertEquals(6, book.getChunksDone());
	}

	@Test
	void extract_phases_savesCharacterThenSceneStatusThenReady() {
		// given
		var chapters = List.of(chapter(10L, 0, 0, TEXT.length()));
		givenStoredBook(chapters);
		var savedStatuses = new ArrayList<BookStatus>();
		when(bookRepository.save(book)).thenAnswer(invocation -> {
			savedStatuses.add(book.getStatus());
			return book;
		});
		var service = serviceWithChunkTokens(WHOLE_TEXT_TOKENS, WHOLE_TEXT_TOKENS);
		when(extractionClient.extractCharacters(prompt(1, TEXT), List.of())).thenReturn(response(List.of(), 10, 5));
		when(sceneClient.extractScenes(prompt(1, TEXT), List.of(), List.of())).thenReturn(scenes(NO_SCENES));
		givenEmptyWorld();
		var expectedStatuses = List.of(BookStatus.EXTRACTING_CHARACTERS, BookStatus.EXTRACTING_CHARACTERS,
				BookStatus.EXTRACTING_SCENES, BookStatus.EXTRACTING_SCENES, BookStatus.READY);

		// when
		service.extract(BOOK_ID);

		// then
		assertEquals(expectedStatuses, savedStatuses);
	}

	@Test
	void extract_rerun_deletesPreviousResultsFirst() {
		// given
		var chapters = List.of(chapter(10L, 0, 0, TEXT.length()));
		givenStoredBook(chapters);
		var service = serviceWithChunkTokens(WHOLE_TEXT_TOKENS, WHOLE_TEXT_TOKENS);
		var oldScenes = List.of(BookScene.builder().id(1L).bookId(BOOK_ID).summary("old").build());
		var oldLocations = List.of(BookLocation.builder().id(2L).bookId(BOOK_ID).name("old").build());
		var oldClues = List.of(SettingClue.builder().id(3L).bookId(BOOK_ID).build());
		var oldSetting = BookSetting.builder().bookId(BOOK_ID).build();
		var oldCharacters = List.of(BookCharacter.builder().id(4L).bookId(BOOK_ID).canonicalName("old").build());
		when(sceneRepository.findAllByBookId(BOOK_ID)).thenReturn(oldScenes);
		when(locationRepository.findAllByBookId(BOOK_ID)).thenReturn(oldLocations);
		when(clueRepository.findAllByBookId(BOOK_ID)).thenReturn(oldClues);
		when(settingRepository.findById(BOOK_ID)).thenReturn(Optional.of(oldSetting));
		when(characterRepository.findAllByBookId(BOOK_ID)).thenReturn(oldCharacters);
		when(extractionClient.extractCharacters(prompt(1, TEXT), List.of())).thenReturn(response(List.of(), 10, 5));
		when(sceneClient.extractScenes(prompt(1, TEXT), List.of(), List.of())).thenReturn(scenes(NO_SCENES));
		givenEmptyWorld();

		// when
		service.extract(BOOK_ID);

		// then
		verify(sceneRepository).deleteAll(oldScenes);
		verify(locationRepository).deleteAll(oldLocations);
		verify(clueRepository).deleteAll(oldClues);
		verify(settingRepository).delete(oldSetting);
		verify(characterRepository).deleteAll(oldCharacters);
		assertEquals(BookStatus.READY, book.getStatus());
	}

	@Test
	void extract_bookMissing_nothingSaved() {
		// given
		when(bookRepository.findById(BOOK_ID)).thenReturn(Optional.empty());

		// when
		service.extract(BOOK_ID);

		// then
		verify(bookRepository, never()).save(any(Book.class));
		verify(extractionClient, never()).extractCharacters(any(), anyList());
		verify(sceneClient, never()).extractScenes(any(), anyList(), anyList());
	}

	@Test
	void extract_failureWhileRecordingFailure_doesNotThrow() {
		// given
		when(bookRepository.findById(BOOK_ID)).thenThrow(new IllegalStateException("database down"));

		// when
		service.extract(BOOK_ID);

		// then
		verify(bookRepository, never()).save(any(Book.class));
	}

	private BookExtractionServiceImpl serviceWithChunkTokens(int chunkTokens, int sceneChunkTokens) {
		return serviceWithChunkTokens(chunkTokens, sceneChunkTokens, SCENE_PARALLELISM);
	}

	private BookExtractionServiceImpl serviceWithChunkTokens(int chunkTokens, int sceneChunkTokens,
															 int sceneParallelism) {
		return service(new AnthropicProperties.Pricing(BigDecimal.ONE, BigDecimal.ONE, 100_000, BigDecimal.ONE,
				BigDecimal.ONE), chunkTokens, sceneChunkTokens, sceneParallelism);
	}

	private BookExtractionServiceImpl service(AnthropicProperties.Pricing pricing, int chunkTokens,
											  int sceneChunkTokens, int sceneParallelism) {
		var properties = new AnthropicProperties("key", "https://api.anthropic.com", "claude-haiku-5-5", 32000,
				new AnthropicProperties.Effort("medium", "low"), Duration.ofMinutes(1), 0, pricing);
		var quoteLocator = new QuoteLocator();
		return new BookExtractionServiceImpl(bookRepository, contentRepository, chapterRepository,
				characterRepository, sceneRepository, locationRepository, settingRepository, clueRepository,
				extractionClient, sceneClient, new BookChunker(), quoteLocator, new CharacterMerger(),
				new SceneAssembler(quoteLocator), new LocationMerger(), new SettingAssembler(quoteLocator),
				new CostCalculator(properties), new ExtractionProperties(chunkTokens, sceneChunkTokens, 1,
				sceneParallelism));
	}

	private void givenStoredBook(List<BookChapter> chapters) {
		when(bookRepository.findById(BOOK_ID)).thenReturn(Optional.of(book));
		when(bookRepository.save(book)).thenReturn(book);
		when(contentRepository.findById(BOOK_ID))
				.thenReturn(Optional.of(BookContent.builder().bookId(BOOK_ID).text(TEXT).build()));
		when(chapterRepository.findAllByBookIdOrderByChapterIndex(BOOK_ID)).thenReturn(chapters);
	}

	private void givenNoCharacters() {
		when(extractionClient.extractCharacters(prompt(1, CHAPTER_1), List.of()))
				.thenReturn(response(List.of(), 10, 5));
		when(extractionClient.extractCharacters(prompt(2, CHAPTER_2), List.of()))
				.thenReturn(response(List.of(), 10, 5));
		when(extractionClient.extractCharacters(prompt(3, CHAPTER_3), List.of()))
				.thenReturn(response(List.of(), 10, 5));
	}

	private void givenNoScenes() {
		when(sceneClient.extractScenes(prompt(1, CHAPTER_1), List.of(), List.of())).thenReturn(scenes(NO_SCENES));
		when(sceneClient.extractScenes(prompt(2, CHAPTER_2), List.of(), List.of())).thenReturn(scenes(NO_SCENES));
		when(sceneClient.extractScenes(prompt(3, CHAPTER_3), List.of(), List.of())).thenReturn(scenes(NO_SCENES));
	}

	private void givenEmptyWorld() {
		when(sceneClient.synthesizeWorld(EMPTY_WORLD_INPUT))
				.thenReturn(new ExtractionResponse<>(world(List.of()), new TokenUsage(10, 5)));
	}

	private static List<BookChapter> threeChapters() {
		var second = TEXT.indexOf(CHAPTER_2);
		var third = TEXT.indexOf(CHAPTER_3);
		return List.of(
				chapter(10L, 0, 0, CHAPTER_1.length()),
				chapter(11L, 1, second, second + CHAPTER_2.length()),
				chapter(12L, 2, third, TEXT.length()));
	}

	private static BookChapter chapter(Long id, int index, int start, int end) {
		return BookChapter.builder().id(id).bookId(BOOK_ID).chapterIndex(index).title("Chapter " + (index + 1))
				.startOffset(start).endOffset(end).build();
	}

	private static String prompt(int chapterNumber, String chapterText) {
		return "[[CHAPTER " + chapterNumber + ": Chapter " + chapterNumber + "]]\n" + chapterText + "\n\n";
	}

	private static ExtractedObservation observation(ObservationKind kind, ObservationCategory category,
													String description, String quote, int chapter) {
		return new ExtractedObservation(kind, category, description, quote, chapter, false, false);
	}

	/**
	 * A scene that starts and ends with the same quote.
	 */
	private static ExtractedScene scene(String quote, int chapter, String location, String locationDescription,
										String character, boolean speaks) {
		return new ExtractedScene(quote, quote, chapter, false, "summary", speaks,
				List.of(new ScenePresence(character, speaks)), location, locationDescription, "", TimeOfDay.DAY, "",
				false, "");
	}

	private static WorldSynthesis world(List<SynthesizedLocation> locations) {
		return new WorldSynthesis(Realism.REALISTIC, "drama", "c. 1880", EvidenceSource.INFERRED, "England",
				"gas lamps", "bustles and frock coats", "brick terraces", "foggy", "Victorian England, gas lamps",
				locations);
	}

	private static ExtractionResponse<ChunkScenes> scenes(ChunkScenes scenes) {
		return new ExtractionResponse<>(scenes, new TokenUsage(1000, 100));
	}

	private static ExtractionResponse<ChunkExtraction> response(List<ExtractedCharacter> characters, long input,
																long output) {
		return response("en", characters, input, output);
	}

	private static ExtractionResponse<ChunkExtraction> response(String language, List<ExtractedCharacter> characters,
																long input, long output) {
		return new ExtractionResponse<>(new ChunkExtraction(language, characters), new TokenUsage(input, output));
	}

	private static List<CharacterSnapshot> snapshots(List<BookCharacter> characters) {
		return characters.stream()
				.map(character -> new CharacterSnapshot(character.getCanonicalName(), character.getSex(),
						character.getFirstOffset(),
						character.getAliases().stream().map(alias -> alias.getAlias()).toList(),
						character.getObservations().stream()
								.map(o -> new LocatedObservation(o.getKind(), o.getCategory(), o.getDescription(),
										o.getQuote(), o.getStartOffset(), o.getEndOffset(), o.getChapterId(),
										o.isExplicitChange(), o.isInferred()))
								.toList()))
				.toList();
	}

	private static List<LocationSnapshot> locationSnapshots(List<BookLocation> locations) {
		return locations.stream()
				.map(location -> new LocationSnapshot(location.getName(), location.getVisualDescription(),
						location.getFirstOffset()))
				.toList();
	}

	private static List<SceneSnapshot> sceneSnapshots(List<BookScene> scenes) {
		return scenes.stream()
				.map(scene -> new SceneSnapshot(scene.getSceneIndex(), scene.getChapterId(), scene.getStartOffset(),
						scene.getEndOffset(), scene.getLocation() == null ? null : scene.getLocation().getName(),
						scene.getCharacters().stream()
								.map(c -> c.getCharacter().getCanonicalName() + ":" + c.isSpeaks())
								.toList()))
				.toList();
	}

	private static List<ClueSnapshot> clueSnapshots(List<SettingClue> clues) {
		return clues.stream()
				.map(clue -> new ClueSnapshot(clue.getCategory(), clue.getDescription(), clue.getStartOffset(),
						clue.getChapterId()))
				.toList();
	}

	private static SettingSnapshot settingSnapshot(BookSetting setting) {
		return new SettingSnapshot(setting.getBookId(), setting.getRealism(), setting.getPeriod(),
				setting.getPeriodSource(), setting.getPromptLine());
	}

	private static BookSnapshot snapshot(Book book) {
		return new BookSnapshot(book.getStatus(), book.getChunksTotal(), book.getChunksDone(), book.getInputTokens(),
				book.getOutputTokens(), book.getCostUsd(), book.getErrorMessage(), book.getLanguage());
	}

	record CharacterSnapshot(String canonicalName, CharacterSex sex, Integer firstOffset, List<String> aliases,
							 List<LocatedObservation> observations) {
	}

	record LocationSnapshot(String name, String visualDescription, Integer firstOffset) {
	}

	record SceneSnapshot(int sceneIndex, Long chapterId, Integer startOffset, Integer endOffset, String location,
						 List<String> characters) {
	}

	record ClueSnapshot(SettingClueCategory category, String description, Integer startOffset, Long chapterId) {
	}

	record SettingSnapshot(Long bookId, Realism realism, String period, EvidenceSource periodSource,
						   String promptLine) {
	}

	record BookSnapshot(BookStatus status, Integer chunksTotal, int chunksDone, long inputTokens, long outputTokens,
						BigDecimal costUsd, String errorMessage, String language) {
	}
}
