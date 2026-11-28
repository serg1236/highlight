package com.highlight.service.impl;

import com.highlight.ai.*;
import com.highlight.ai.WorldSynthesisInput.ClueSummary;
import com.highlight.ai.WorldSynthesisInput.LocationSummary;
import com.highlight.config.ExtractionProperties;
import com.highlight.entity.*;
import com.highlight.exception.ResourceNotFoundException;
import com.highlight.repository.*;
import com.highlight.service.BookExtractionService;
import com.highlight.service.support.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Extracts in two passes: characters over large chunks, one after another ({@code EXTRACTING_CHARACTERS}), then
 * scenes and setting clues over small chunks (small so the model reports every scene), several at once
 * ({@code EXTRACTING_SCENES}). The character roster is carried forward so names stay consistent, and every scene
 * call sees the complete roster of the first pass plus the location names of earlier scene waves. Then, still in
 * {@code EXTRACTING_SCENES}, merges characters across chunks, synthesizes the world setting and locations from the
 * whole book, and stores everything with offsets located in the book text.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookExtractionServiceImpl implements BookExtractionService {

	private static final int MAX_ERROR_LENGTH = 1000;

	private static final int MAX_MERGE_TRAITS = 3;

	private static final int MAX_LOCATION_DESCRIPTIONS = 3;

	private final BookRepository bookRepository;

	private final BookContentRepository contentRepository;

	private final BookChapterRepository chapterRepository;

	private final BookCharacterRepository characterRepository;

	private final BookSceneRepository sceneRepository;

	private final BookLocationRepository locationRepository;

	private final BookSettingRepository settingRepository;

	private final SettingClueRepository clueRepository;

	private final CharacterExtractionClient extractionClient;

	private final SceneExtractionClient sceneClient;

	private final BookChunker chunker;

	private final QuoteLocator quoteLocator;

	private final CharacterMerger merger;

	private final SceneAssembler sceneAssembler;

	private final LocationMerger locationMerger;

	private final SettingAssembler settingAssembler;

	private final CostCalculator costCalculator;

	private final ExtractionProperties properties;

	@Override
	public void extract(Long bookId) {
		try {
			runExtraction(bookId);
		} catch (Exception e) {
			log.error("Extraction failed: bookId={}", bookId, e);
			markFailed(bookId, e);
		}
	}

	private void runExtraction(Long bookId) {
		var book = bookRepository.findById(bookId)
				.orElseThrow(() -> new ResourceNotFoundException("Book " + bookId + " not found"));
		var text = contentRepository.findById(bookId)
				.orElseThrow(() -> new ResourceNotFoundException("Text of book " + bookId + " not found"))
				.getText();
		var chapters = chapterRepository.findAllByBookIdOrderByChapterIndex(bookId);
		var chunks = chunker.split(text, chapters, properties.chunkMaxTokens());
		var sceneChunks = chunker.split(text, chapters, properties.sceneChunkMaxTokens());
		book = startExtraction(book, chunks.size() + sceneChunks.size());
		deletePreviousResults(bookId);

		var roster = new Roster();
		var candidates = new ArrayList<CandidateCharacter>();
		var detectedLanguages = new ArrayList<String>();
		for (var chunk : chunks) {
			var response = extractionClient.extractCharacters(chunk.promptText(text), roster.entries());
			var characters = response.result().characters();
			detectedLanguages.add(response.result().language());
			for (var extracted : characters) {
				candidates.add(toCandidate(candidates.size(), extracted, chunk, text, chapters, bookId));
				roster.add(extracted);
			}
			book = recordProgress(book, response.usage());
			log.info("Characters extracted: bookId={}, chunk={}/{}, characters={}, costUsd={}", bookId,
					chunk.index() + 1, chunks.size(), characters.size(), book.getCostUsd());
		}

		book = startPhase(book, BookStatus.EXTRACTING_SCENES, sceneChunks.size());
		var scenePass = extractScenes(book, text, chapters, sceneChunks, roster.entries());
		book = scenePass.book();
		var clues = scenePass.clues();
		var locationSummaries = scenePass.locationNames().summaries();

		var characters = merger.assemble(bookId, candidates, mergeClusters(book, chunks.size(), candidates));
		characterRepository.saveAll(characters);
		var world = synthesizeWorld(book, clues, locationSummaries);
		var locations = locationMerger.merge(bookId, locationSummaries, world.locations());
		var scenes = sceneAssembler.assemble(bookId, text, chapters, scenePass.batches(), characters,
				locations);
		locationRepository.saveAll(locations.locations());
		sceneRepository.saveAll(scenes);
		settingRepository.save(settingAssembler.toSetting(bookId, world));
		clueRepository.saveAll(clues);
		correctLanguage(book, detectedLanguages);
		book.setStatus(BookStatus.READY);
		book = bookRepository.save(book);
		log.info("Extraction finished: bookId={}, chunks={}, candidates={}, characters={}, scenes={}, locations={}, "
				 + "clues={}, inputTokens={}, outputTokens={}, costUsd={}", bookId, chunks.size(), candidates.size(),
				characters.size(), scenes.size(), locations.locations().size(), clues.size(), book.getInputTokens(),
				book.getOutputTokens(), book.getCostUsd());
	}

	/**
	 * Runs the scene calls in waves of up to {@code sceneParallelism} chunks on a per-book pool. Every chunk of a
	 * wave sees the location names of all earlier waves; results are applied in chunk order on the calling thread,
	 * so the book and the accumulators are never touched concurrently.
	 */
	private ScenePass extractScenes(Book book, String text, List<BookChapter> chapters, List<BookChunk> sceneChunks,
									List<RosterEntry> knownCharacters) {
		var bookId = book.getId();
		var locationNames = new LocationNames();
		var sceneBatches = new ArrayList<ChunkSceneBatch>();
		var clues = new ArrayList<SettingClue>();
		var parallelism = properties.sceneParallelism();
		var threads = Thread.ofPlatform().name("scene-" + bookId + "-", 0).factory();
		var executor = Executors.newFixedThreadPool(parallelism, threads);
		try {
			for (var waveStart = 0; waveStart < sceneChunks.size(); waveStart += parallelism) {
				var wave = sceneChunks.subList(waveStart, Math.min(waveStart + parallelism, sceneChunks.size()));
				var knownLocations = locationNames.names();
				var futures = wave.stream()
						.map(chunk -> executor.submit(() -> sceneClient.extractScenes(chunk.promptText(text),
								knownCharacters, knownLocations)))
						.toList();
				for (var i = 0; i < wave.size(); i++) {
					var chunk = wave.get(i);
					var response = await(futures.get(i), bookId, chunk);
					var scenes = response.result().scenes();
					sceneBatches.add(new ChunkSceneBatch(chunk, scenes));
					scenes.forEach(locationNames::add);
					var chunkClues = settingAssembler.toClues(bookId, text, chapters, chunk,
							response.result().settingClues());
					clues.addAll(chunkClues);
					book = recordProgress(book, response.usage());
					log.info("Scenes extracted: bookId={}, chunk={}/{}, scenes={}, clues={}, costUsd={}", bookId,
							chunk.index() + 1, sceneChunks.size(), scenes.size(), chunkClues.size(),
							book.getCostUsd());
				}
			}
		} finally {
			// Does not wait: after a failure the rest of the wave is interrupted and dropped.
			executor.shutdownNow();
		}
		return new ScenePass(book, sceneBatches, clues, locationNames);
	}

	private static ExtractionResponse<ChunkScenes> await(Future<ExtractionResponse<ChunkScenes>> future, Long bookId,
														 BookChunk chunk) {
		try {
			return future.get();
		} catch (ExecutionException e) {
			if (e.getCause() instanceof RuntimeException cause) {
				throw cause;
			}
			throw new IllegalStateException("Scene extraction failed: bookId=" + bookId + ", chunk=" + chunk.index(),
					e.getCause());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Scene extraction interrupted: bookId=" + bookId + ", chunk="
											+ chunk.index(), e);
		}
	}

	private void deletePreviousResults(Long bookId) {
		sceneRepository.deleteAll(sceneRepository.findAllByBookId(bookId));
		locationRepository.deleteAll(locationRepository.findAllByBookId(bookId));
		clueRepository.deleteAll(clueRepository.findAllByBookId(bookId));
		settingRepository.findById(bookId).ifPresent(settingRepository::delete);
		characterRepository.deleteAll(characterRepository.findAllByBookId(bookId));
	}

	private Book startPhase(Book book, BookStatus status, int chunkCount) {
		book.setStatus(status);
		book = bookRepository.save(book);
		log.info("Extraction phase started: bookId={}, status={}, chunks={}", book.getId(), status, chunkCount);
		return book;
	}

	private Book startExtraction(Book book, int chunkCount) {
		book.setStatus(BookStatus.EXTRACTING_CHARACTERS);
		book.setChunksTotal(chunkCount);
		book.setChunksDone(0);
		book.setInputTokens(0);
		book.setOutputTokens(0);
		book.setCostUsd(BigDecimal.ZERO);
		book.setErrorMessage(null);
		return bookRepository.save(book);
	}

	private Book recordProgress(Book book, TokenUsage usage) {
		addUsage(book, usage);
		book.setChunksDone(book.getChunksDone() + 1);
		return bookRepository.save(book);
	}

	private void addUsage(Book book, TokenUsage usage) {
		book.setInputTokens(book.getInputTokens() + usage.inputTokens());
		book.setOutputTokens(book.getOutputTokens() + usage.outputTokens());
		book.setCostUsd(book.getCostUsd().add(costCalculator.cost(usage)));
	}

	private WorldSynthesis synthesizeWorld(Book book, List<SettingClue> clues, List<LocationSummary> locations) {
		var clueSummaries = clues.stream()
				.map(clue -> new ClueSummary(clue.getCategory(), clue.getDescription(), clue.getQuote()))
				.toList();
		var response = sceneClient.synthesizeWorld(
				new WorldSynthesisInput(book.getTitle(), book.getAuthor(), clueSummaries, locations));
		addUsage(book, response.usage());
		log.info("World synthesized: bookId={}, clues={}, locationNames={}, locations={}, realism={}, period={}",
				book.getId(), clues.size(), locations.size(), response.result().locations().size(),
				response.result().realism(), response.result().period());
		return response.result();
	}

	/**
	 * Replaces the metadata language with the one the model saw in most chunks, unless the metadata already names
	 * it (as a tag like {@code en-US} or an English name like {@code English}).
	 */
	private void correctLanguage(Book book, List<String> detectedLanguages) {
		var detected = mostFrequent(detectedLanguages);
		if (detected.isEmpty() || sameLanguage(book.getLanguage(), detected.get())) {
			return;
		}
		log.info("Book language corrected: bookId={}, from={}, to={}", book.getId(), book.getLanguage(),
				detected.get());
		book.setLanguage(detected.get());
	}

	private static Optional<String> mostFrequent(List<String> languages) {
		var counts = languages.stream()
				.filter(Objects::nonNull)
				.map(language -> language.strip().toLowerCase(Locale.ROOT))
				.filter(language -> !language.isEmpty())
				.collect(Collectors.groupingBy(Function.identity(), LinkedHashMap::new, Collectors.counting()));
		return counts.entrySet().stream()
				.max(Comparator.comparingLong(Map.Entry::getValue))
				.map(Map.Entry::getKey);
	}

	private static boolean sameLanguage(String current, String code) {
		if (current == null || current.isBlank()) {
			return false;
		}
		var normalized = current.strip();
		var primaryTag = normalized.split("[-_]", 2)[0];
		return primaryTag.equalsIgnoreCase(code)
			   || normalized.equalsIgnoreCase(Locale.forLanguageTag(code).getDisplayLanguage(Locale.ENGLISH));
	}

	/**
	 * Each chunk call already deduplicates within its excerpt, so the merge call is only needed across chunks.
	 */
	private List<MergeCluster> mergeClusters(Book book, int chunkCount, List<CandidateCharacter> candidates) {
		if (chunkCount < 2 || candidates.size() < 2) {
			return List.of();
		}
		var response = extractionClient.mergeCharacters(candidates.stream().map(this::toMergeCandidate).toList());
		addUsage(book, response.usage());
		log.info("Characters merged: bookId={}, candidates={}, clusters={}", book.getId(), candidates.size(),
				response.result().clusters().size());
		return response.result().clusters();
	}

	private MergeCandidate toMergeCandidate(CandidateCharacter candidate) {
		var traits = candidate.observations().stream()
				.map(LocatedObservation::description)
				.limit(MAX_MERGE_TRAITS)
				.toList();
		return new MergeCandidate(candidate.id(), candidate.name(), candidate.aliases(), candidate.sex(), traits);
	}

	private CandidateCharacter toCandidate(int id, ExtractedCharacter extracted, BookChunk chunk, String text,
										   List<BookChapter> chapters, Long bookId) {
		var observations = extracted.observations().stream()
				.map(observation -> locate(observation, chunk, text, chapters, bookId, extracted.name()))
				.toList();
		var aliases = extracted.aliases().stream()
				.filter(Objects::nonNull)
				.map(String::strip)
				.filter(alias -> !alias.isEmpty() && !alias.equalsIgnoreCase(extracted.name().strip()))
				.distinct()
				.toList();
		var sex = Objects.requireNonNullElse(extracted.sex(), CharacterSex.UNDEFINED);
		return new CandidateCharacter(id, extracted.name().strip(), aliases, sex, observations);
	}

	private LocatedObservation locate(ExtractedObservation observation, BookChunk chunk, String text,
									  List<BookChapter> chapters, Long bookId, String characterName) {
		var span = quoteLocator.locate(text, chunk.startOffset(), chunk.endOffset(), observation.quote());
		if (span.isEmpty()) {
			log.warn("Quote not found in text: bookId={}, chunk={}, character={}, quote={}", bookId, chunk.index(),
					characterName, observation.quote());
		}
		return new LocatedObservation(observation.kind(), observation.category(), observation.description(),
				observation.quote(), span.map(found -> found.start()).orElse(null),
				span.map(found -> found.end()).orElse(null), ChapterLookup.chapterId(chapters, span,
				observation.chapter()), observation.explicitChange(), observation.inferred());
	}

	private void markFailed(Long bookId, Exception cause) {
		try {
			bookRepository.findById(bookId).ifPresent(book -> {
				book.setStatus(BookStatus.FAILED);
				book.setErrorMessage(truncate(cause.getClass().getSimpleName() + ": " + cause.getMessage()));
				bookRepository.save(book);
			});
		} catch (RuntimeException e) {
			log.error("Could not record extraction failure: bookId={}", bookId, e);
		}
	}

	private static String truncate(String message) {
		return message.length() <= MAX_ERROR_LENGTH ? message : message.substring(0, MAX_ERROR_LENGTH);
	}

	/**
	 * What the scene pass collected, plus the book as last saved.
	 */
	private record ScenePass(Book book, List<ChunkSceneBatch> batches, List<SettingClue> clues,
							 LocationNames locationNames) {
	}

	/**
	 * Location names the scenes used so far, in first-use order, with up to a few distinct descriptions each.
	 * Sent with each chunk to keep location names consistent, and to the world synthesis at the end.
	 */
	private static final class LocationNames {

		private final Map<String, Entry> entries = new LinkedHashMap<>();

		void add(ExtractedScene scene) {
			var name = scene.location();
			if (name == null || name.isBlank()) {
				return;
			}
			var entry = entries.computeIfAbsent(name.strip().toLowerCase(Locale.ROOT),
					key -> new Entry(name.strip(), new LinkedHashSet<>()));
			var description = scene.locationDescription();
			if (description != null && !description.isBlank()
				&& entry.descriptions().size() < MAX_LOCATION_DESCRIPTIONS) {
				entry.descriptions().add(description.strip());
			}
		}

		List<String> names() {
			return entries.values().stream().map(Entry::name).toList();
		}

		List<LocationSummary> summaries() {
			return entries.values().stream()
					.map(entry -> new LocationSummary(entry.name(), List.copyOf(entry.descriptions())))
					.toList();
		}

		private record Entry(String name, LinkedHashSet<String> descriptions) {
		}
	}

	/**
	 * Characters seen so far, keyed by any of their names, sent with each chunk to keep naming consistent.
	 */
	private static final class Roster {

		private final List<Entry> entries = new ArrayList<>();

		List<RosterEntry> entries() {
			return entries.stream()
					.map(entry -> new RosterEntry(entry.name(), List.copyOf(entry.aliases())))
					.toList();
		}

		void add(ExtractedCharacter character) {
			var names = new ArrayList<String>();
			names.add(character.name());
			names.addAll(character.aliases());
			var entry = entries.stream().filter(existing -> existing.matchesAny(names)).findFirst().orElse(null);
			if (entry == null) {
				entries.add(new Entry(character.name().strip(), new LinkedHashSet<>(clean(character.aliases()))));
				return;
			}
			names.stream()
					.map(String::strip)
					.filter(name -> !name.isEmpty() && !entry.matchesAny(List.of(name)))
					.forEach(entry.aliases()::add);
		}

		private static List<String> clean(List<String> aliases) {
			return aliases.stream().filter(Objects::nonNull).map(String::strip).filter(a -> !a.isEmpty()).toList();
		}

		private record Entry(String name, LinkedHashSet<String> aliases) {

			boolean matchesAny(List<String> names) {
				return names.stream()
						.filter(Objects::nonNull)
						.map(name -> name.strip().toLowerCase(Locale.ROOT))
						.anyMatch(name -> name.equals(this.name.toLowerCase(Locale.ROOT))
										  || aliases.stream().anyMatch(alias -> alias.toLowerCase(Locale.ROOT).equals(name)));
			}
		}
	}
}
