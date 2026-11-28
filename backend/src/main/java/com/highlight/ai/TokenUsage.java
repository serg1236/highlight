package com.highlight.ai;

/**
 * Tokens billed for one Claude call. {@code outputTokens} includes thinking tokens.
 */
public record TokenUsage(long inputTokens, long outputTokens) {
}
