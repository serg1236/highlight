package com.highlight.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.time.Duration;

/**
 * Claude API connection, model and pricing settings ({@code highlight.anthropic.*}).
 */
@Validated
@ConfigurationProperties(prefix = "highlight.anthropic")
public record AnthropicProperties(
		@NotBlank String apiKey,
		@NotBlank String baseUrl,
		@NotBlank String model,
		@Positive long maxTokens,
		@Valid @NotNull Effort effort,
		@NotNull Duration timeout,
		@PositiveOrZero int maxRetries,
		@Valid @NotNull Pricing pricing) {

	/**
	 * Effort per pass ({@code low}, {@code medium}, {@code high}, {@code xhigh} or {@code max}). {@code character}
	 * applies to character extraction and the character merge; {@code scene} applies to scene extraction and the
	 * world synthesis.
	 */
	public record Effort(
			@NotBlank String character,
			@NotBlank String scene) {
	}

	/**
	 * Model prices in USD per one million tokens.
	 */
	public record Pricing(
			@NotNull @PositiveOrZero BigDecimal input,
			@NotNull @PositiveOrZero BigDecimal output,
			@Positive long longContextThreshold,
			@NotNull @PositiveOrZero BigDecimal longContextInput,
			@NotNull @PositiveOrZero BigDecimal longContextOutput) {
	}
}
