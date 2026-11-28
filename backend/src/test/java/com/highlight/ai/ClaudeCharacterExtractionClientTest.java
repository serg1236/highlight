package com.highlight.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.highlight.config.AnthropicProperties;
import com.highlight.entity.CharacterSex;
import com.highlight.entity.ObservationCategory;
import com.highlight.entity.ObservationKind;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ClaudeCharacterExtractionClientTest {

	private static final String MODEL = "claude-haiku-5-5";

	private WireMockServer server;

	private AnthropicClient anthropicClient;

	private ClaudeCharacterExtractionClient client;

	@BeforeEach
	void setUp() {
		server = new WireMockServer(options().dynamicPort());
		server.start();
		anthropicClient = AnthropicOkHttpClient.builder()
				.apiKey("test-key")
				.baseUrl(server.baseUrl())
				.maxRetries(0)
				.build();
		var properties = new AnthropicProperties("test-key", server.baseUrl(), MODEL, 32000, new AnthropicProperties.Effort("medium", "low"),
				Duration.ofSeconds(30), 0, new AnthropicProperties.Pricing(BigDecimal.ONE, BigDecimal.ONE, 100_000,
				BigDecimal.ONE, BigDecimal.ONE));
		client = new ClaudeCharacterExtractionClient(new ClaudeStructuredCaller(anthropicClient, properties),
				properties);
	}

	@AfterEach
	void tearDown() {
		anthropicClient.close();
		server.stop();
	}

	@Test
	void extractCharacters_parsesStructuredOutputAndUsage() {
		// given
		var json = """
				{"language":"en","characters":[{"name":"Elizabeth Bennet","aliases":["Lizzy"],"sex":"FEMALE","observations":[
				{"kind":"APPEARANCE","category":"EYES","description":"fine dark eyes",
				"quote":"her fine eyes","chapter":6,"explicitChange":false,"inferred":false},
				{"kind":"ROLE","category":"SOCIAL_CLASS","description":"daughter of a country gentleman",
				"quote":"a gentleman's daughter","chapter":6,"explicitChange":false,"inferred":true}]}]}""";
		stubMessage("end_turn", json, 1200, 340);
		var expected = new ExtractionResponse<>(new ChunkExtraction("en", List.of(new ExtractedCharacter(
				"Elizabeth Bennet", List.of("Lizzy"), CharacterSex.FEMALE, List.of(new ExtractedObservation(ObservationKind.APPEARANCE,
						ObservationCategory.EYES, "fine dark eyes", "her fine eyes", 6, false, false),
				new ExtractedObservation(ObservationKind.ROLE, ObservationCategory.SOCIAL_CLASS,
						"daughter of a country gentleman", "a gentleman's daughter", 6, false, true))))),
				new TokenUsage(1200, 340));

		// when
		var actual = client.extractCharacters("[[CHAPTER 6: untitled]]\ntext",
				List.of(new RosterEntry("Jane Bennet", List.of("Jane"))));

		// then
		assertEquals(expected, actual);
		server.verify(postRequestedFor(urlEqualTo("/v1/messages"))
				.withHeader("Authorization", equalTo("Bearer test-key"))
				.withRequestBody(matchingJsonPath("$.model", equalTo(MODEL)))
				.withRequestBody(matchingJsonPath("$.max_tokens", equalTo("32000")))
				.withRequestBody(matchingJsonPath("$.output_config.effort", equalTo("medium")))
				.withRequestBody(matchingJsonPath("$.output_config.format.type", equalTo("json_schema")))
				.withRequestBody(matchingJsonPath("$.system", containing("character data from novel excerpts")))
				.withRequestBody(matchingJsonPath("$.messages[0].content",
						containing("- Jane Bennet (also: Jane)\n\n<excerpt>\n[[CHAPTER 6: untitled]]\ntext</excerpt>"))));
	}

	@Test
	void extractCharacters_emptyRoster_saysNoneYet() {
		// given
		stubMessage("end_turn", "{\"language\":\"uk\",\"characters\":[]}", 10, 5);

		// when
		var actual = client.extractCharacters("text", List.of());

		// then
		assertEquals(new ExtractionResponse<>(new ChunkExtraction("uk", List.of()), new TokenUsage(10, 5)), actual);
		server.verify(postRequestedFor(urlEqualTo("/v1/messages"))
				.withRequestBody(matchingJsonPath("$.messages[0].content", containing("Known characters:\n(none yet)"))));
	}

	@Test
	void mergeCharacters_sendsCandidatesAndParsesClusters() {
		// given
		stubMessage("end_turn", "{\"clusters\":[{\"canonicalName\":\"Elizabeth Bennet\",\"candidateIds\":[0,1]}]}",
				500, 40);
		var candidates = List.of(
				new MergeCandidate(0, "Elizabeth Bennet", List.of("Lizzy"), CharacterSex.FEMALE,
						List.of("fine dark eyes")),
				new MergeCandidate(1, "Eliza", List.of(), CharacterSex.UNDEFINED, List.of()));
		var expected = new ExtractionResponse<>(
				new MergeDecision(List.of(new MergeCluster("Elizabeth Bennet", List.of(0, 1)))),
				new TokenUsage(500, 40));

		// when
		var actual = client.mergeCharacters(candidates);

		// then
		assertEquals(expected, actual);
		server.verify(postRequestedFor(urlEqualTo("/v1/messages"))
				.withRequestBody(matchingJsonPath("$.output_config.effort", equalTo("medium")))
				.withRequestBody(matchingJsonPath("$.system", containing("deduplicate characters")))
				.withRequestBody(matchingJsonPath("$.messages[0].content", equalTo("Candidates:\n"
																				   + "id=0 | name: Elizabeth Bennet | aliases: Lizzy | sex: FEMALE | traits: fine dark eyes\n"
																				   + "id=1 | name: Eliza | aliases:  | sex: UNDEFINED | traits: "))));
	}

	@ParameterizedTest
	@CsvSource({"refusal", "max_tokens"})
	void extractCharacters_unusableStopReason_shouldThrowException(String stopReason) {
		stubMessage(stopReason, "{\"characters\":[]}", 10, 5);

		assertThrows(ExtractionException.class, () -> client.extractCharacters("text", List.of()));
	}

	@Test
	void extractCharacters_noTextContent_shouldThrowException() {
		server.stubFor(post(urlEqualTo("/v1/messages")).willReturn(aResponse()
				.withHeader("Content-Type", "application/json")
				.withBody(messageJson("end_turn", "[]", 10, 5))));

		assertThrows(ExtractionException.class, () -> client.extractCharacters("text", List.of()));
	}

	private void stubMessage(String stopReason, String outputJson, long inputTokens, long outputTokens) {
		var content = "[{\"type\":\"text\",\"text\":" + jsonString(outputJson) + "}]";
		server.stubFor(post(urlEqualTo("/v1/messages")).willReturn(aResponse()
				.withHeader("Content-Type", "application/json")
				.withBody(messageJson(stopReason, content, inputTokens, outputTokens))));
	}

	private static String messageJson(String stopReason, String content, long inputTokens, long outputTokens) {
		return """
				{"id":"msg_test","type":"message","role":"assistant","model":"%s","content":%s,
				"stop_reason":"%s","stop_sequence":null,
				"usage":{"input_tokens":%d,"output_tokens":%d}}""".formatted(MODEL, content, stopReason, inputTokens,
				outputTokens);
	}

	private static String jsonString(String value) {
		return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
	}
}
