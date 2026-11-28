package com.highlight.ai;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

public record SynthesizedLocation(
		@JsonPropertyDescription("The clearest of the given names for this place")
		String name,
		@JsonPropertyDescription("Every given location name that refers to this place, including name itself")
		List<String> mergedNames,
		@JsonPropertyDescription("Visual description in English for an image prompt, combining the given "
								 + "descriptions with what is typical for the setting; at most 40 words")
		String visualDescription) {
}
