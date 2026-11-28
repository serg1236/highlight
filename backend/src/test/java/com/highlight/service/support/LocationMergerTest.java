package com.highlight.service.support;

import com.highlight.ai.SynthesizedLocation;
import com.highlight.ai.WorldSynthesisInput.LocationSummary;
import com.highlight.entity.BookLocation;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LocationMergerTest {

	private static final Long BOOK_ID = 4L;

	private final LocationMerger merger = new LocationMerger();

	@Test
	void merge_mergedNamesShareOneLocation_leftOutNamesGetTheirOwn() {
		// given
		var summaries = List.of(
				new LocationSummary("Netherfield", List.of("large house")),
				new LocationSummary("Netherfield Park", List.of()),
				new LocationSummary("Longbourn", List.of(" ", "small estate")));
		var synthesized = List.of(new SynthesizedLocation("Netherfield", List.of("Netherfield Park"),
				" Georgian country house "));
		var expectedLocations = List.of(
				new LocationSnapshot(BOOK_ID, "Netherfield", "Georgian country house"),
				new LocationSnapshot(BOOK_ID, "Longbourn", "small estate"));
		var expectedNames = Map.of("netherfield", "Netherfield", "netherfield park", "Netherfield",
				"longbourn", "Longbourn");

		// when
		var actual = merger.merge(BOOK_ID, summaries, synthesized);

		// then
		assertEquals(expectedLocations, snapshots(actual.locations()));
		assertEquals(expectedNames, names(actual.byName()));
	}

	@Test
	void merge_defensiveAgainstModelMistakes() {
		// given: an invented place, a name used twice, a blank name and description, a repeated location name
		var summaries = List.of(
				new LocationSummary("Meryton", List.of("market town")),
				new LocationSummary("Meryton assembly rooms", List.of("crowded ballroom")),
				new LocationSummary("Lucas Lodge", List.of()),
				new LocationSummary(" ", List.of("ignored")));
		var synthesized = List.of(
				new SynthesizedLocation("Pemberley", List.of("Pemberley"), "grand estate"),
				new SynthesizedLocation(" ", Arrays.asList("MERYTON", null, " "), " "),
				new SynthesizedLocation("Meryton", List.of("Meryton", "Meryton assembly rooms"), "assembly hall"),
				new SynthesizedLocation("meryton ", List.of("Lucas Lodge"), "town house"));
		var expectedLocations = List.of(new LocationSnapshot(BOOK_ID, "Meryton", "market town"));
		var expectedNames = Map.of("meryton", "Meryton", "meryton assembly rooms", "Meryton",
				"lucas lodge", "Meryton");

		// when
		var actual = merger.merge(BOOK_ID, summaries, synthesized);

		// then
		assertEquals(expectedLocations, snapshots(actual.locations()));
		assertEquals(expectedNames, names(actual.byName()));
		assertEquals("Meryton", actual.find(" Meryton Assembly Rooms ").getName());
		assertEquals(null, actual.find(null));
	}

	@Test
	void merge_noLocations_empty() {
		// when
		var actual = merger.merge(BOOK_ID, List.of(), List.of());

		// then
		assertEquals(new MergedLocations(List.of(), Map.of()), actual);
	}

	private static List<LocationSnapshot> snapshots(List<BookLocation> locations) {
		return locations.stream()
				.map(location -> new LocationSnapshot(location.getBookId(), location.getName(),
						location.getVisualDescription()))
				.toList();
	}

	private static Map<String, String> names(Map<String, BookLocation> byName) {
		var names = new TreeMap<String, String>();
		byName.forEach((name, location) -> names.put(name, location.getName()));
		return names;
	}

	record LocationSnapshot(Long bookId, String name, String visualDescription) {
	}
}
