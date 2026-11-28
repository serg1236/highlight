package com.highlight.ai;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public record ScenePresence(
		@JsonPropertyDescription("Fullest proper name; reuse a known character's name exactly when it is the same person")
		String name,
		@JsonPropertyDescription("True when this person has at least one line of dialog in the scene")
		boolean speaks) {
}
