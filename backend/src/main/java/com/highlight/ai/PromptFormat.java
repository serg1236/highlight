package com.highlight.ai;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Shared formatting of prompt sections.
 */
final class PromptFormat {

	static final String NONE_YET = "(none yet)";

	private PromptFormat() {
	}

	static String roster(List<RosterEntry> roster) {
		if (roster.isEmpty()) {
			return NONE_YET;
		}
		return roster.stream()
				.map(entry -> "- " + entry.name()
							  + (entry.aliases().isEmpty() ? "" : " (also: " + String.join(", ", entry.aliases()) + ")"))
				.collect(Collectors.joining("\n"));
	}

	static String bullets(List<String> items) {
		if (items.isEmpty()) {
			return NONE_YET;
		}
		return items.stream().map(item -> "- " + item).collect(Collectors.joining("\n"));
	}
}
