package com.highlight.ai;

import com.highlight.config.AnthropicProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Claude implementation of scene extraction and world synthesis, using structured outputs.
 */
@Component
@RequiredArgsConstructor
public class ClaudeSceneExtractionClient implements SceneExtractionClient {

	static final String SCENE_PROMPT = """
			You split novel excerpts into scenes and collect evidence about the story's setting. The results \
			drive a comic illustration pipeline: every scene gets its own background picture, so a missed scene \
			is a missed background. Report every scene, but never invent events or details.
			
			Scenes:
			- A scene is continuous action in one place and time. Start a new scene whenever the place changes \
			(another room, going outside, getting into or out of a vehicle, arriving somewhere), time jumps, or \
			the narration switches to other characters. A typical chapter has several scenes.
			- A scene never crosses a [[CHAPTER n: ...]] marker: every chapter that contains narration has at \
			least one scene of its own.
			- A passage that summarizes a longer period is one scene only while it stays in one place; when the \
			summary moves between places, split it.
			- Non-narrative text is not a scene: skip epigraphs, dedications, author notes, tables of contents.
			- Cover all narration of the excerpt in reading order, without gaps or overlaps.
			- startQuote and endQuote: copy the first and last 10-20 words of the scene exactly, in the \
			original language, character for character.
			- continuesPrevious: true only for the first scene, and only when the excerpt opens in the middle \
			of a scene that started before it.
			- characters: only named people physically present. If the person is in the known characters list, \
			use that exact name. speaks is true when they have at least one line of dialog.
			- location: a short, specific place name, always in English even when the book is in another \
			language (e.g. "Björn's apartment", "office meeting room"). If the place is in the known locations \
			list, use that exact name.
			- locationDescription: what the text says the place looks like, in English. Leave it empty when the \
			text describes nothing; never invent details.
			- locationQuote: the sentence that describes the place, copied exactly; empty when none.
			- summary, timeMarker and timelineNote are in English.
			- timeOfDay: UNKNOWN unless the text makes it clear.
			- timelineShift: true for flashbacks, dreams, visions or scenes in another world.
			
			Setting clues:
			- Up to 8 of the strongest pieces of evidence in the excerpt about when and where the story is set: \
			PERIOD (dates, historical events, era markers), REGION (countries, cities, cultures), TECHNOLOGY \
			(transport, communication, weapons, lighting), MAGIC (spells, supernatural beings), CUSTOM (social \
			rules, forms of address), CLOTHING (fashions in general, not one character's outfit), ARCHITECTURE \
			(buildings, interiors).
			- description in English; quote copied exactly; chapter from the nearest [[CHAPTER n: ...]] marker.
			- Return an empty list when the excerpt has no such evidence.
			""";

	static final String WORLD_PROMPT = """
			You describe the world of a novel for an illustration pipeline, from evidence extracted from the \
			whole book: setting clues with quotes, and the locations its scenes use.
			
			- Base every field on the clues. You may use general knowledge to turn a period and region into \
			typical clothing, architecture and light, but never contradict a clue.
			- realism: REALISTIC unless the clues show magic, the supernatural or speculative technology. MIXED \
			when the story moves between our world and another.
			- period: as concrete as the evidence allows (decade or century, era name). periodSource is CITED \
			only when a clue states the period or a datable event; otherwise INFERRED.
			- clothingNorms: typical dress by class, gender and age in this world.
			- promptLine: at most 40 words an image prompt can use as-is: period, place, typical dress and \
			architecture. No character names, no plot, no art style.
			- locations: one entry per real place. Put every given location name in exactly one entry's \
			mergedNames; merge names that clearly refer to the same place ("Netherfield", "Netherfield Park"). \
			Add no new places. visualDescription combines the given descriptions with what is typical for the \
			setting.
			""";

	private static final String UNKNOWN = "unknown";

	private final ClaudeStructuredCaller caller;

	private final AnthropicProperties properties;

	@Override
	public ExtractionResponse<ChunkScenes> extractScenes(String chunkText, List<RosterEntry> knownCharacters,
														 List<String> knownLocations) {
		var userMessage = "Known characters:\n" + PromptFormat.roster(knownCharacters)
						  + "\n\nKnown locations:\n" + PromptFormat.bullets(knownLocations)
						  + "\n\n<excerpt>\n" + chunkText + "</excerpt>";
		return caller.call(SCENE_PROMPT, userMessage, ChunkScenes.class, properties.effort().scene());
	}

	@Override
	public ExtractionResponse<WorldSynthesis> synthesizeWorld(WorldSynthesisInput input) {
		var clues = input.clues().stream()
				.map(clue -> "[" + clue.category() + "] " + clue.description() + " | quote: " + clue.quote())
				.toList();
		var locations = input.locations().stream()
				.map(location -> location.name()
								 + (location.descriptions().isEmpty() ? "" : ": " + String.join("; ", location.descriptions())))
				.toList();
		var userMessage = "Title: " + valueOrUnknown(input.title())
						  + "\nAuthor: " + valueOrUnknown(input.author())
						  + "\n\nSetting clues:\n" + PromptFormat.bullets(clues)
						  + "\n\nLocations:\n" + PromptFormat.bullets(locations);
		return caller.call(WORLD_PROMPT, userMessage, WorldSynthesis.class, properties.effort().scene());
	}

	private static String valueOrUnknown(String value) {
		return value == null || value.isBlank() ? UNKNOWN : value;
	}
}
