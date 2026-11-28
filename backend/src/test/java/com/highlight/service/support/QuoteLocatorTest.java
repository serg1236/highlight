package com.highlight.service.support;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QuoteLocatorTest {

	private static final String TEXT = "She had “long, dark   hair” and wore a red—scarlet cloak.\n\nHe was tall.";

	private final QuoteLocator locator = new QuoteLocator();

	static Stream<Arguments> locatableQuotes() {
		var cloak = TEXT.indexOf("red—scarlet cloak");
		var hair = TEXT.indexOf("long, dark");
		var tall = TEXT.indexOf("He was tall");
		return Stream.of(
				Arguments.of("exact match", "red—scarlet cloak", new TextSpan(cloak, cloak + 17)),
				Arguments.of("straight quotes and collapsed spaces", "\"long, dark hair\"",
						new TextSpan(hair, TEXT.indexOf("hair") + 4)),
				Arguments.of("case and dash differences", "RED-SCARLET CLOAK.", new TextSpan(cloak, cloak + 17)),
				Arguments.of("ellipsis around quote", "…He was tall…", new TextSpan(tall, tall + 11)),
				Arguments.of("quote spanning a paragraph break", "cloak. He was",
						new TextSpan(TEXT.indexOf("cloak"), tall + 6)));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("locatableQuotes")
	void locate_found(String useCase, String quote, TextSpan expected) {
		assertEquals(Optional.of(expected), locator.locate(TEXT, 0, TEXT.length(), quote));
	}

	@Test
	void locate_absentQuote_empty() {
		assertEquals(Optional.empty(), locator.locate(TEXT, 0, TEXT.length(), "blue eyes"));
	}

	@Test
	void locate_quoteOutsideRegion_empty() {
		assertEquals(Optional.empty(), locator.locate(TEXT, 0, 20, "He was tall"));
	}

	@Test
	void locate_quoteBeforeRegion_empty() {
		assertEquals(Optional.empty(), locator.locate(TEXT, 30, TEXT.length(), "She had"));
	}

	@Test
	void locate_tooShortQuote_empty() {
		assertEquals(Optional.empty(), locator.locate(TEXT, 0, TEXT.length(), "\"He\""));
	}

	@Test
	void locate_nullQuote_empty() {
		assertEquals(Optional.empty(), locator.locate(TEXT, 0, TEXT.length(), null));
	}
}
