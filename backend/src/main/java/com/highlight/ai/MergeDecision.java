package com.highlight.ai;

import java.util.List;

/**
 * Structured output of the merge call: candidates grouped by real person.
 */
public record MergeDecision(List<MergeCluster> clusters) {
}
