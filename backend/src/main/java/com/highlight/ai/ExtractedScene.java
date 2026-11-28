package com.highlight.ai;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.highlight.entity.TimeOfDay;

import java.util.List;

public record ExtractedScene(
		@JsonPropertyDescription("First 10-20 words of the scene, copied from the excerpt character for character")
		String startQuote,
		@JsonPropertyDescription("Last 10-20 words of the scene, copied from the excerpt character for character")
		String endQuote,
		@JsonPropertyDescription("Number from the nearest [[CHAPTER n: ...]] marker before the scene start")
		int chapter,
		@JsonPropertyDescription("True only for the first scene when the excerpt starts in the middle of a scene")
		boolean continuesPrevious,
		@JsonPropertyDescription("What happens, in English, 1-2 sentences")
		String summary,
		@JsonPropertyDescription("True when characters speak to each other in the scene")
		boolean hasDialog,
		@JsonPropertyDescription("Named characters physically present in the scene")
		List<ScenePresence> characters,
		@JsonPropertyDescription("Short specific place name in English, e.g. 'Longbourn drawing room'; reuse a "
								 + "known location name exactly when it is the same place")
		String location,
		@JsonPropertyDescription("Visual description of the place in English as the text gives it; empty when the "
								 + "text describes nothing")
		String locationDescription,
		@JsonPropertyDescription("Exact sentence describing the place, copied from the excerpt; empty when none")
		String locationQuote,
		TimeOfDay timeOfDay,
		@JsonPropertyDescription("Time reference from the text, e.g. 'the next morning', 'that winter'; empty when none")
		String timeMarker,
		@JsonPropertyDescription("True for flashbacks, dreams, visions or another world, i.e. outside the main "
								 + "timeline and place of the story")
		boolean timelineShift,
		@JsonPropertyDescription("What the shift is, e.g. 'flashback to her childhood'; empty when no shift")
		String timelineNote) {
}
