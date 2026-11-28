package com.highlight.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Extraction job settings ({@code highlight.extraction.*}). {@code chunkMaxTokens} sizes the chunks of the character
 * pass; {@code sceneChunkMaxTokens} sizes the smaller chunks of the scene pass, which must split its excerpt into
 * every scene. {@code sceneParallelism} caps the concurrent scene calls of one book, so a deployment makes at most
 * {@code concurrentBooks × sceneParallelism} model calls at once.
 */
@Validated
@ConfigurationProperties(prefix = "highlight.extraction")
public record ExtractionProperties(
		@Min(1_000) @Max(900_000) int chunkMaxTokens,
		@Min(1_000) @Max(900_000) int sceneChunkMaxTokens,
		@Positive int concurrentBooks,
		@Positive int sceneParallelism) {
}
