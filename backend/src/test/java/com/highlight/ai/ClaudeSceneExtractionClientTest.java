package com.highlight.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.highlight.ai.WorldSynthesisInput.ClueSummary;
import com.highlight.ai.WorldSynthesisInput.LocationSummary;
import com.highlight.config.AnthropicProperties;
import com.highlight.entity.EvidenceSource;
import com.highlight.entity.Realism;
import com.highlight.entity.SettingClueCategory;
import com.highlight.entity.TimeOfDay;
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

class ClaudeSceneExtractionClientTest {

	private static final String MODEL = "claude-haiku-5-5";

	private WireMockServer server;

	private AnthropicClient anthropicClient;

	private ClaudeSceneExtractionClient client;

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
		client = new ClaudeSceneExtractionClient(new ClaudeStructuredCaller(anthropicClient, properties), properties);
	}

	@AfterEach
	void tearDown() {
		anthropicClient.close();
		server.stop();
	}

	@Test
	void extractScenes_parsesStructuredOutputAndSendsRosters() {
		// given
		var json = """
				{"scenes":[{"startQuote":"The ball at Meryton","endQuote":"she laughed","chapter":3,
				"continuesPrevious":false,"summary":"Darcy snubs Elizabeth at the assembly.","hasDialog":true,
				"characters":[{"name":"Elizabeth Bennet","speaks":true},{"name":"Mr. Darcy","speaks":true}],
				"location":"Meryton assembly rooms","locationDescription":"crowded ballroom",
				"locationQuote":"","timeOfDay":"EVENING","timeMarker":"","timelineShift":false,"timelineNote":""}],
				"settingClues":[{"category":"TECHNOLOGY","description":"travel by carriage",
				"quote":"the carriage was ordered","chapter":3}]}""";
		stubMessage("end_turn", json, 2000, 400);
		var expected = new ExtractionResponse<>(new ChunkScenes(
				List.of(new ExtractedScene("The ball at Meryton", "she laughed", 3, false,
						"Darcy snubs Elizabeth at the assembly.", true,
						List.of(new ScenePresence("Elizabeth Bennet", true), new ScenePresence("Mr. Darcy", true)),
						"Meryton assembly rooms", "crowded ballroom", "", TimeOfDay.EVENING, "", false, "")),
				List.of(new ExtractedSettingClue(SettingClueCategory.TECHNOLOGY, "travel by carriage",
						"the carriage was ordered", 3))),
				new TokenUsage(2000, 400));

		// when
		var actual = client.extractScenes("[[CHAPTER 3: untitled]]\ntext",
				List.of(new RosterEntry("Elizabeth Bennet", List.of("Lizzy"))), List.of("Longbourn"));

		// then
		assertEquals(expected, actual);
		server.verify(postRequestedFor(urlEqualTo("/v1/messages"))
				.withRequestBody(matchingJsonPath("$.model", equalTo(MODEL)))
				.withRequestBody(matchingJsonPath("$.output_config.effort", equalTo("low")))
				.withRequestBody(matchingJsonPath("$.output_config.format.type", equalTo("json_schema")))
				.withRequestBody(matchingJsonPath("$.system", containing("split novel excerpts into scenes")))
				.withRequestBody(matchingJsonPath("$.messages[0].content", equalTo("Known characters:\n"
																				   + "- Elizabeth Bennet (also: Lizzy)\n\nKnown locations:\n- Longbourn\n\n"
																				   + "<excerpt>\n[[CHAPTER 3: untitled]]\ntext</excerpt>"))));
	}

	@Test
	void extractScenes_emptyRosters_sayNoneYet() {
		// given
		stubMessage("end_turn", "{\"scenes\":[],\"settingClues\":[]}", 10, 5);

		// when
		var actual = client.extractScenes("text", List.of(), List.of());

		// then
		assertEquals(new ExtractionResponse<>(new ChunkScenes(List.of(), List.of()), new TokenUsage(10, 5)), actual);
		server.verify(postRequestedFor(urlEqualTo("/v1/messages"))
				.withRequestBody(matchingJsonPath("$.messages[0].content",
						containing("Known characters:\n(none yet)\n\nKnown locations:\n(none yet)"))));
	}

	@Test
	void synthesizeWorld_sendsEvidenceAndParsesWorld() {
		// given
		var json = """
				{"realism":"REALISTIC","genre":"comedy of manners","period":"c. 1811, Regency",
				"periodSource":"INFERRED","region":"rural England","technology":"carriages, candles",
				"clothingNorms":"empire-waist gowns, tailcoats","architecture":"Georgian manors",
				"visualMood":"soft daylight","promptLine":"Regency England, empire-waist gowns, Georgian manors",
				"locations":[{"name":"Netherfield","mergedNames":["Netherfield","Netherfield Park"],
				"visualDescription":"large Georgian country house"}]}""";
		stubMessage("end_turn", json, 800, 300);
		var input = new WorldSynthesisInput("Pride and Prejudice", null,
				List.of(new ClueSummary(SettingClueCategory.TECHNOLOGY, "travel by carriage", "the carriage")),
				List.of(new LocationSummary("Netherfield", List.of("large house", "park")),
						new LocationSummary("Netherfield Park", List.of())));
		var expected = new ExtractionResponse<>(new WorldSynthesis(Realism.REALISTIC, "comedy of manners",
				"c. 1811, Regency", EvidenceSource.INFERRED, "rural England", "carriages, candles",
				"empire-waist gowns, tailcoats", "Georgian manors", "soft daylight",
				"Regency England, empire-waist gowns, Georgian manors",
				List.of(new SynthesizedLocation("Netherfield", List.of("Netherfield", "Netherfield Park"),
						"large Georgian country house"))),
				new TokenUsage(800, 300));

		// when
		var actual = client.synthesizeWorld(input);

		// then
		assertEquals(expected, actual);
		server.verify(postRequestedFor(urlEqualTo("/v1/messages"))
				.withRequestBody(matchingJsonPath("$.output_config.effort", equalTo("low")))
				.withRequestBody(matchingJsonPath("$.system", containing("describe the world of a novel")))
				.withRequestBody(matchingJsonPath("$.messages[0].content", equalTo("Title: Pride and Prejudice\n"
																				   + "Author: unknown\n\nSetting clues:\n"
																				   + "- [TECHNOLOGY] travel by carriage | quote: the carriage\n\nLocations:\n"
																				   + "- Netherfield: large house; park\n- Netherfield Park"))));
	}

	@ParameterizedTest
	@CsvSource({"refusal", "max_tokens"})
	void extractScenes_unusableStopReason_shouldThrowException(String stopReason) {
		stubMessage(stopReason, "{\"scenes\":[],\"settingClues\":[]}", 10, 5);

		assertThrows(ExtractionException.class, () -> client.extractScenes("text", List.of(), List.of()));
	}

	private void stubMessage(String stopReason, String outputJson, long inputTokens, long outputTokens) {
		var content = "[{\"type\":\"text\",\"text\":" + jsonString(outputJson) + "}]";
		server.stubFor(post(urlEqualTo("/v1/messages")).willReturn(aResponse()
				.withHeader("Content-Type", "application/json")
				.withBody("""
						{"id":"msg_test","type":"message","role":"assistant","model":"%s","content":%s,
						"stop_reason":"%s","stop_sequence":null,
						"usage":{"input_tokens":%d,"output_tokens":%d}}""".formatted(MODEL, content, stopReason,
						inputTokens, outputTokens))));
	}

	private static String jsonString(String value) {
		return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
	}
}
