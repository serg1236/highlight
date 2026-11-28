package com.highlight.ai;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

/**
 * Structured output of one scene extraction call.
 */
public record ChunkScenes(
		@JsonPropertyDescription("Every scene of the excerpt, in reading order; a new scene at every change of place "
								 + "or time and at every chapter marker")
		List<ExtractedScene> scenes,
		@JsonPropertyDescription("At most 8 of the strongest clues about when and where the story is set")
		List<ExtractedSettingClue> settingClues) {
}
