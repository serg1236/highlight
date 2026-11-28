package com.highlight.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.*;
import com.highlight.config.AnthropicProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Sends one Claude Messages API call with structured output, so the response always matches the record schema.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClaudeStructuredCaller {

	private final AnthropicClient anthropicClient;

	private final AnthropicProperties properties;

	/**
	 * @param effort the API effort level for this call, e.g. {@code low} or {@code medium}
	 * @throws ExtractionException when the model refuses, runs out of tokens or returns no content
	 */
	public <T> ExtractionResponse<T> call(String systemPrompt, String userMessage, Class<T> type, String effort) {
		var params = MessageCreateParams.builder()
				.model(properties.model())
				.maxTokens(properties.maxTokens())
				.system(systemPrompt)
				.outputConfig(StructuredOutputConfig.<T>builder()
						.effort(OutputConfig.Effort.of(effort))
						.format(type)
						.build())
				.addUserMessage(userMessage)
				.build();
		var response = anthropicClient.messages().create(params);
		var usage = new TokenUsage(response.usage().inputTokens(), response.usage().outputTokens());
		log.info("Claude call finished: type={}, effort={}, stopReason={}, inputTokens={}, outputTokens={}",
				type.getSimpleName(), effort, response.stopReason().orElse(null), usage.inputTokens(),
				usage.outputTokens());
		return new ExtractionResponse<>(resultOf(response, type), usage);
	}

	private static <T> T resultOf(StructuredMessage<T> response, Class<T> type) {
		var stopReason = response.stopReason().orElse(null);
		if (StopReason.REFUSAL.equals(stopReason) || StopReason.MAX_TOKENS.equals(stopReason)) {
			throw new ExtractionException("Model stopped with " + stopReason + " while producing "
										  + type.getSimpleName());
		}
		return response.content().stream()
				.flatMap(block -> block.text().stream())
				.map(textBlock -> textBlock.text())
				.findFirst()
				.orElseThrow(() -> new ExtractionException("Model returned no " + type.getSimpleName()));
	}
}
