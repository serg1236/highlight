package com.highlight.ai;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.highlight.entity.CharacterSex;

import java.util.List;

public record ExtractedCharacter(
		@JsonPropertyDescription("Fullest proper name; reuse a known character's name exactly when it is the same person")
		String name,
		@JsonPropertyDescription("Other names, nicknames, surnames and titles the text uses for this person; no pronouns")
		List<String> aliases,
		@JsonPropertyDescription("MALE or FEMALE when the text makes it clear (pronouns, gendered titles or nouns); "
								 + "UNDEFINED otherwise")
		CharacterSex sex,
		List<ExtractedObservation> observations) {
}
