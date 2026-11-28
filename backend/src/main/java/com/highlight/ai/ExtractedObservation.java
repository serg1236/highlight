package com.highlight.ai;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.highlight.entity.ObservationCategory;
import com.highlight.entity.ObservationKind;

public record ExtractedObservation(
		ObservationKind kind,
		@JsonPropertyDescription("APPEARANCE: HAIR, EYES, FACE, SKIN, BUILD, HEIGHT, AGE, MARKS, OTHER. "
								 + "CLOTHING: OUTFIT, TOP, BOTTOM, OUTERWEAR, FOOTWEAR, HEADWEAR, ACCESSORY. "
								 + "ROLE: OCCUPATION, SOCIAL_CLASS (at most one of each per person per excerpt)")
		ObservationCategory category,
		@JsonPropertyDescription("Short normalized phrase, always in English regardless of the quote's language, "
								 + "e.g. 'long auburn hair'")
		String description,
		@JsonPropertyDescription("Exact sentence or clause from the excerpt, copied character for character")
		String quote,
		@JsonPropertyDescription("Number from the nearest preceding [[CHAPTER n: ...]] marker")
		int chapter,
		@JsonPropertyDescription("True only when the text says the appearance, clothing or role changed")
		boolean explicitChange,
		@JsonPropertyDescription("True when the quote implies the fact rather than stating it; only for AGE and ROLE")
		boolean inferred) {
}
