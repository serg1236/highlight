package com.highlight.service.support;

import com.highlight.ai.TokenUsage;
import com.highlight.config.AnthropicProperties;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CostCalculatorTest {

	private final CostCalculator calculator = new CostCalculator(new AnthropicProperties("key",
			"https://api.anthropic.com", "claude-haiku-5-5", 32000, new AnthropicProperties.Effort("medium", "low"),
			Duration.ofMinutes(5), 2,
			new AnthropicProperties.Pricing(new BigDecimal("0.10"), new BigDecimal("0.50"), 100_000,
					new BigDecimal("0.50"), new BigDecimal("2.50"))));

	@ParameterizedTest(name = "{0} in / {1} out -> {2}")
	@CsvSource({
			"0, 0, 0.000000",
			"50000, 0, 0.005000",
			"1000000, 0, 0.500000",
			"0, 1000000, 0.500000",
			"72000, 8000, 0.011200",
			"100000, 10000, 0.015000",
			"100001, 0, 0.050001",
			"200000, 10000, 0.125000"
	})
	void cost(long inputTokens, long outputTokens, BigDecimal expected) {
		assertEquals(expected, calculator.cost(new TokenUsage(inputTokens, outputTokens)));
	}
}
