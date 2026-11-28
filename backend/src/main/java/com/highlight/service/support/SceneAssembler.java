package com.highlight.service.support;

import com.highlight.ai.ExtractedScene;
import com.highlight.ai.ScenePresence;
import com.highlight.entity.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Stream;

/**
 * Turns the scenes reported per chunk into scenes of the whole book. Offsets come from locating the start and
 * end quotes in the book text, never from the model:
 * <ul>
 * <li>A scene whose start quote cannot be found keeps no offsets.</li>
 * <li>A scene ends where its end quote ends; otherwise, or when that lies past the next located scene start,
 * at the next located scene start; otherwise at the end of the chunk.</li>
 * <li>A first scene marked {@code continuesPrevious} extends the last scene of the previous chunk.</li>
 * </ul>
 * Characters are matched by canonical name or alias, case-insensitively. Names matching no character, or more
 * than one, are skipped.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SceneAssembler {

	private final QuoteLocator quoteLocator;

	public List<BookScene> assemble(Long bookId, String text, List<BookChapter> chapters,
									List<ChunkSceneBatch> batches, List<BookCharacter> characters, MergedLocations locations) {
		var names = nameIndex(characters);
		var scenes = new ArrayList<BookScene>();
		for (var batch : batches) {
			var starts = locateStarts(text, batch);
			for (var i = 0; i < batch.scenes().size(); i++) {
				var extracted = batch.scenes().get(i);
				if (i == 0 && extracted.continuesPrevious() && !scenes.isEmpty()) {
					var end = endOf(text, batch, starts, i, batch.chunk().startOffset());
					extend(scenes.getLast(), extracted, end, names, locations);
					continue;
				}
				var start = starts.get(i);
				var end = start.isPresent() ? endOf(text, batch, starts, i, start.get().start()) : null;
				var scene = BookScene.builder()
						.bookId(bookId)
						.sceneIndex(scenes.size())
						.chapterId(ChapterLookup.chapterId(chapters, start, extracted.chapter()))
						.startOffset(start.map(TextSpan::start).orElse(null))
						.endOffset(end)
						.summary(nullToEmpty(extracted.summary()))
						.hasDialog(extracted.hasDialog())
						.locationQuote(blankToNull(extracted.locationQuote()))
						.timeOfDay(extracted.timeOfDay() == null ? TimeOfDay.UNKNOWN : extracted.timeOfDay())
						.timeMarker(blankToNull(extracted.timeMarker()))
						.timelineShift(extracted.timelineShift())
						.timelineNote(blankToNull(extracted.timelineNote()))
						.build();
				if (start.isEmpty()) {
					log.warn("Scene start not found in text: bookId={}, chunk={}, scene={}, quote={}", bookId,
							batch.chunk().index(), scene.getSceneIndex(), extracted.startQuote());
				}
				setLocation(scene, locations.find(extracted.location()));
				addPresences(scene, extracted.characters(), names);
				scenes.add(scene);
			}
		}
		return scenes;
	}

	private List<Optional<TextSpan>> locateStarts(String text, ChunkSceneBatch batch) {
		var chunk = batch.chunk();
		var cursor = chunk.startOffset();
		var starts = new ArrayList<Optional<TextSpan>>();
		for (var scene : batch.scenes()) {
			var span = quoteLocator.locate(text, cursor, chunk.endOffset(), scene.startQuote());
			if (span.isPresent()) {
				cursor = span.get().end();
			}
			starts.add(span);
		}
		return starts;
	}

	private int endOf(String text, ChunkSceneBatch batch, List<Optional<TextSpan>> starts, int sceneIndex,
					  int searchFrom) {
		var chunkEnd = batch.chunk().endOffset();
		var nextStart = starts.stream()
				.skip(sceneIndex + 1L)
				.flatMap(Optional::stream)
				.map(TextSpan::start)
				.findFirst();
		var located = quoteLocator.locate(text, searchFrom, chunkEnd, batch.scenes().get(sceneIndex).endQuote())
				.map(TextSpan::end);
		if (located.isPresent() && (nextStart.isEmpty() || located.get() <= nextStart.get())) {
			return located.get();
		}
		return nextStart.orElse(chunkEnd);
	}

	private static void extend(BookScene scene, ExtractedScene continuation, int end,
							   Map<String, Optional<BookCharacter>> names, MergedLocations locations) {
		if (scene.getStartOffset() != null) {
			scene.setEndOffset(end);
		}
		var summary = nullToEmpty(continuation.summary());
		if (!summary.isEmpty()) {
			scene.setSummary(scene.getSummary().isEmpty() ? summary : scene.getSummary() + " " + summary);
		}
		scene.setHasDialog(scene.isHasDialog() || continuation.hasDialog());
		if (scene.getLocation() == null) {
			setLocation(scene, locations.find(continuation.location()));
		}
		if (scene.getLocationQuote() == null) {
			scene.setLocationQuote(blankToNull(continuation.locationQuote()));
		}
		addPresences(scene, continuation.characters(), names);
	}

	private static void setLocation(BookScene scene, BookLocation location) {
		scene.setLocation(location);
		var start = scene.getStartOffset();
		if (location != null && start != null
			&& (location.getFirstOffset() == null || start < location.getFirstOffset())) {
			location.setFirstOffset(start);
		}
	}

	private static void addPresences(BookScene scene, List<ScenePresence> presences,
									 Map<String, Optional<BookCharacter>> names) {
		for (var presence : presences) {
			if (presence.name() == null || presence.name().isBlank()) {
				continue;
			}
			var match = names.get(key(presence.name()));
			if (match == null || match.isEmpty()) {
				log.warn("Scene character not resolved: bookId={}, scene={}, name={}, reason={}", scene.getBookId(),
						scene.getSceneIndex(), presence.name(), match == null ? "unknown" : "ambiguous");
				continue;
			}
			var character = match.get();
			scene.getCharacters().stream()
					.filter(existing -> existing.getCharacter() == character)
					.findFirst()
					.ifPresentOrElse(existing -> existing.setSpeaks(existing.isSpeaks() || presence.speaks()),
							() -> scene.addCharacter(SceneCharacter.builder()
									.character(character)
									.speaks(presence.speaks())
									.build()));
		}
	}

	/**
	 * Lower-cased canonical names and aliases; an empty value marks a name shared by several characters.
	 */
	private static Map<String, Optional<BookCharacter>> nameIndex(List<BookCharacter> characters) {
		var index = new HashMap<String, Optional<BookCharacter>>();
		for (var character : characters) {
			Stream.concat(Stream.of(character.getCanonicalName()),
							character.getAliases().stream().map(CharacterAlias::getAlias))
					.filter(name -> name != null && !name.isBlank())
					.map(SceneAssembler::key)
					.distinct()
					.forEach(name -> index.merge(name, Optional.of(character),
							(existing, added) -> existing.equals(added) ? existing : Optional.empty()));
		}
		return index;
	}

	private static String key(String name) {
		return name.strip().toLowerCase(Locale.ROOT);
	}

	private static String nullToEmpty(String value) {
		return value == null ? "" : value.strip();
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.strip();
	}
}
