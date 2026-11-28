package com.highlight.service.support;

import com.highlight.ai.TokenUsage;
import com.highlight.config.AnthropicProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Prices one Claude call from its reported token usage and the configured per-million-token rates.
 * Prompts above the long-context threshold are billed at the long-context rates for the whole call.
 */
@Component
@RequiredArgsConstructor
public class CostCalculator {

	private static final BigDecimal ONE_MILLION = BigDecimal.valueOf(1_000_000);

	private static final int SCALE = 6;

	private final AnthropicProperties properties;

	public BigDecimal cost(TokenUsage usage) {
		var pricing = properties.pricing();
		var longContext = usage.inputTokens() > pricing.longContextThreshold();
		var inputRate = longContext ? pricing.longContextInput() : pricing.input();
		var outputRate = longContext ? pricing.longContextOutput() : pricing.output();
		return BigDecimal.valueOf(usage.inputTokens()).multiply(inputRate)
				.add(BigDecimal.valueOf(usage.outputTokens()).multiply(outputRate))
				.divide(ONE_MILLION, SCALE, RoundingMode.HALF_UP);
	}
}
