package com.highlight.ai;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.highlight.entity.EvidenceSource;
import com.highlight.entity.Realism;

import java.util.List;

/**
 * Structured output of the world synthesis call: the book's setting as concrete visual facts.
 */
public record WorldSynthesis(
		Realism realism,
		@JsonPropertyDescription("Short genre label, e.g. 'romantic comedy of manners'")
		String genre,
		@JsonPropertyDescription("Concrete time, e.g. 'c. 1811-1813, Regency era'")
		String period,
		@JsonPropertyDescription("CITED only when a clue states the period; INFERRED otherwise")
		EvidenceSource periodSource,
		@JsonPropertyDescription("Region and culture, e.g. 'rural Hertfordshire and London, England'")
		String region,
		@JsonPropertyDescription("Technology level and, if any, magic, e.g. 'horse-drawn carriages, candlelight, "
								 + "letters'")
		String technology,
		@JsonPropertyDescription("Typical clothing by class, gender and age")
		String clothingNorms,
		@JsonPropertyDescription("Typical buildings and interiors")
		String architecture,
		@JsonPropertyDescription("Lighting, palette and mood")
		String visualMood,
		@JsonPropertyDescription("At most 40 words for image prompts: period, place, clothing, architecture. "
								 + "No character names, no plot, no art style")
		String promptLine,
		@JsonPropertyDescription("One entry per real place; every given location name in exactly one entry")
		List<SynthesizedLocation> locations) {
}
