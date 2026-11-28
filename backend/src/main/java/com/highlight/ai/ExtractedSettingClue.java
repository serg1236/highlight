package com.highlight.ai;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.highlight.entity.SettingClueCategory;

public record ExtractedSettingClue(
		SettingClueCategory category,
		@JsonPropertyDescription("What the clue shows, in English, e.g. 'travel by horse-drawn carriage'")
		String description,
		@JsonPropertyDescription("Exact sentence or clause from the excerpt, copied character for character")
		String quote,
		@JsonPropertyDescription("Number from the nearest preceding [[CHAPTER n: ...]] marker")
		int chapter) {
}
