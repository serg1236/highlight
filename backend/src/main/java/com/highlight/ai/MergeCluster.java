package com.highlight.ai;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

public record MergeCluster(
		@JsonPropertyDescription("The person's fullest commonly used name")
		String canonicalName,
		@JsonPropertyDescription("Ids of all candidates that are this person")
		List<Integer> candidateIds) {
}
