package com.highlight.ai;

public record ExtractionResponse<T>(T result, TokenUsage usage) {
}
