package com.highlight.ai;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

/**
 * Structured output of one chunk extraction call.
 */
public record ChunkExtraction(
		@JsonPropertyDescription("ISO 639-1 code of the excerpt's main language, lowercase, e.g. 'en', 'uk', 'fr'")
		String language,
		@JsonPropertyDescription("Named characters who speak, act or are described in this excerpt")
		List<ExtractedCharacter> characters) {
}
