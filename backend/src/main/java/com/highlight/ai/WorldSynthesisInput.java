package com.highlight.ai;

import com.highlight.entity.SettingClueCategory;

import java.util.List;

/**
 * Evidence gathered from the whole book for the world synthesis call.
 */
public record WorldSynthesisInput(String title, String author, List<ClueSummary> clues,
								  List<LocationSummary> locations) {

	public record ClueSummary(SettingClueCategory category, String description, String quote) {
	}

	/**
	 * A location name as the scenes used it, with the distinct descriptions they gave.
	 */
	public record LocationSummary(String name, List<String> descriptions) {
	}
}
