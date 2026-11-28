package com.highlight.config;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
@EnableConfigurationProperties({AnthropicProperties.class, ExtractionProperties.class})
public class AppConfig {

	public static final String EXTRACTION_EXECUTOR = "extractionExecutor";

	@Bean(destroyMethod = "close")
	public AnthropicClient anthropicClient(AnthropicProperties properties) {
		return AnthropicOkHttpClient.builder()
				.apiKey(properties.apiKey())
				.baseUrl(properties.baseUrl())
				.timeout(properties.timeout())
				.maxRetries(properties.maxRetries())
				.build();
	}

	@Bean(name = EXTRACTION_EXECUTOR)
	public Executor extractionExecutor(ExtractionProperties properties) {
		var executor = new ThreadPoolTaskExecutor();
		executor.setThreadNamePrefix("extraction-");
		executor.setCorePoolSize(properties.concurrentBooks());
		executor.setMaxPoolSize(properties.concurrentBooks());
		executor.setQueueCapacity(100);
		executor.setWaitForTasksToCompleteOnShutdown(false);
		executor.initialize();
		return executor;
	}
}
