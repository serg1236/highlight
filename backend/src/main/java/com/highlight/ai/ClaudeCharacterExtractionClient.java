package com.highlight.ai;

import com.highlight.config.AnthropicProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Claude implementation of character extraction and merging, using structured outputs.
 */
@Component
@RequiredArgsConstructor
public class ClaudeCharacterExtractionClient implements CharacterExtractionClient {

	static final String EXTRACTION_PROMPT = """
			You extract character data from novel excerpts. The results drive an illustration pipeline, so \
			every fact must be backed by the text: never invent one.
			
			language: the ISO 639-1 code, lowercase (e.g. "en", "uk", "fr"), of the language the excerpt is \
			written in. Judge it from the narrative text, not from quoted foreign phrases or names.
			
			Report every named character who speaks, acts or is described in the excerpt, even when there are \
			no observations for them. Skip people who are only mentioned in passing. For each one report:
			- name: the fullest proper name used. If the person is in the known characters list, use that \
			exact name.
			- aliases: every other way the text names this specific person (first name, surname, nickname, \
			title such as "Mr. Darcy"). No pronouns and no one-off descriptions like "the stranger".
			- sex: MALE or FEMALE when the excerpt makes it clear, e.g. through pronouns ("he", "she"), \
			gendered titles ("Mrs.", "Lord") or nouns ("the boy", "his sister"). UNDEFINED when the excerpt \
			does not make it clear; never guess from the name alone.
			- observations: facts about the person's physical appearance, clothing and social role. Empty is \
			fine for people the excerpt does not describe.
			
			What to look for: APPEARANCE and CLOTHING are the main goal, because they decide what the \
			illustrations show. Read the whole excerpt for them and report every one the text states, including \
			brief mentions in passing. Report each garment, piece of footwear, headwear and accessory as its own \
			observation: "a dark suit, a white shirt with cufflinks and a silver tie" is an OUTFIT, a TOP and an \
			ACCESSORY. ROLE is secondary.
			
			Observation rules:
			- APPEARANCE and CLOTHING: record only what the text states. Never infer from personality, era, \
			genre or other characters, and never fill gaps. The one exception is AGE, which may be inferred \
			from a strong clue in the excerpt such as "the schoolboy" or "her grandchildren".
			- ROLE: occupation, rank or title (OCCUPATION) and social standing (SOCIAL_CLASS), e.g. "country \
			gentleman", "housekeeper", "army officer", "wealthy landowner". These may be inferred from what the \
			excerpt shows, but never from the genre or era alone. Report at most one OCCUPATION and one \
			SOCIAL_CLASS per person per excerpt, using the clearest quote. Report a second one only when the role \
			changes within the excerpt.
			- APPEARANCE categories: HAIR, EYES, FACE, SKIN, BUILD, HEIGHT, AGE, MARKS (scars, injuries, \
			tattoos, glasses), OTHER.
			- CLOTHING categories: OUTFIT (whole outfit), TOP, BOTTOM, OUTERWEAR, FOOTWEAR, HEADWEAR, ACCESSORY.
			- ROLE categories: OCCUPATION, SOCIAL_CLASS.
			- description: a short phrase usable in an image prompt, e.g. "long auburn hair". Always write \
			it in English, even when the book and the quote are in another language: translate the meaning, \
			never copy the original wording.
			- quote: copy the supporting sentence or clause from the excerpt exactly, in the original \
			language, character for character, at most about 40 words. Do not paraphrase or fix punctuation.
			- chapter: the number from the nearest [[CHAPTER n: ...]] marker before the quote.
			- explicitChange: true only when the text says the appearance, clothing or role changed, e.g. "she \
			had cut her hair short" or "he changed into a black coat".
			- inferred: false when the quote states the fact; true when the quote only implies it (allowed \
			only for AGE and ROLE).
			- When a sentence describes several people, attribute each trait to the right person.
			
			Return an empty list when the excerpt has no named characters.
			""";

	static final String MERGE_PROMPT = """
			You deduplicate characters that were extracted separately from different parts of one novel. \
			Each candidate has an id, a name, aliases and some traits.
			
			Group the candidates into clusters, one cluster per real person:
			- Put candidates in the same cluster when their names or aliases clearly refer to the same person.
			- Keep relatives and other people who only share a surname or title in separate clusters.
			- Candidates with conflicting sex (MALE vs FEMALE) are different people; UNDEFINED never conflicts.
			- Every candidate id must appear in exactly one cluster.
			- canonicalName: the person's fullest commonly used name.
			""";

	private final ClaudeStructuredCaller caller;

	private final AnthropicProperties properties;

	@Override
	public ExtractionResponse<ChunkExtraction> extractCharacters(String chunkText, List<RosterEntry> knownCharacters) {
		var userMessage = "Known characters:\n" + PromptFormat.roster(knownCharacters)
						  + "\n\n<excerpt>\n" + chunkText + "</excerpt>";
		return caller.call(EXTRACTION_PROMPT, userMessage, ChunkExtraction.class, properties.effort().character());
	}

	@Override
	public ExtractionResponse<MergeDecision> mergeCharacters(List<MergeCandidate> candidates) {
		var userMessage = candidates.stream()
				.map(ClaudeCharacterExtractionClient::formatCandidate)
				.collect(Collectors.joining("\n"));
		return caller.call(MERGE_PROMPT, "Candidates:\n" + userMessage, MergeDecision.class,
				properties.effort().character());
	}

	private static String formatCandidate(MergeCandidate candidate) {
		return "id=" + candidate.id()
			   + " | name: " + candidate.name()
			   + " | aliases: " + String.join(", ", candidate.aliases())
			   + " | sex: " + candidate.sex()
			   + " | traits: " + String.join("; ", candidate.traits());
	}
}
