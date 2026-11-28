package com.highlight.service.support;

import com.highlight.ai.SynthesizedLocation;
import com.highlight.ai.WorldSynthesisInput.LocationSummary;
import com.highlight.entity.BookLocation;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Stream;

/**
 * Turns the location names the scenes used and the model's grouping of them into one location per place.
 * The model's answer is treated defensively: names the scenes never used are ignored, a name is used once,
 * names the model left out become their own location, and locations with the same name are combined.
 */
@Component
public class LocationMerger {

	public MergedLocations merge(Long bookId, List<LocationSummary> summaries, List<SynthesizedLocation> synthesized) {
		var known = new LinkedHashMap<String, LocationSummary>();
		summaries.stream()
				.filter(summary -> !isBlank(summary.name()))
				.forEach(summary -> known.putIfAbsent(MergedLocations.key(summary.name()), summary));
		var locations = new LinkedHashMap<String, BookLocation>();
		var byName = new LinkedHashMap<String, BookLocation>();
		var assigned = new HashSet<String>();
		for (var place : synthesized) {
			var members = Stream.concat(Stream.of(place.name()), place.mergedNames().stream())
					.filter(name -> !isBlank(name))
					.map(MergedLocations::key)
					.filter(known::containsKey)
					.filter(assigned::add)
					.toList();
			if (members.isEmpty()) {
				continue;
			}
			var name = isBlank(place.name()) ? known.get(members.getFirst()).name() : place.name();
			var description = isBlank(place.visualDescription())
					? firstDescription(members.stream().map(known::get).toList())
					: place.visualDescription().strip();
			var location = location(locations, bookId, name, description);
			members.forEach(member -> byName.put(member, location));
		}
		for (var entry : known.entrySet()) {
			if (assigned.add(entry.getKey())) {
				var summary = entry.getValue();
				var location = location(locations, bookId, summary.name(), firstDescription(List.of(summary)));
				byName.put(entry.getKey(), location);
			}
		}
		return new MergedLocations(List.copyOf(locations.values()), Map.copyOf(byName));
	}

	private static BookLocation location(Map<String, BookLocation> locations, Long bookId, String name,
										 String description) {
		var location = locations.computeIfAbsent(MergedLocations.key(name), key -> BookLocation.builder()
				.bookId(bookId)
				.name(name.strip())
				.build());
		if (location.getVisualDescription() == null) {
			location.setVisualDescription(description);
		}
		return location;
	}

	private static String firstDescription(List<LocationSummary> summaries) {
		return summaries.stream()
				.flatMap(summary -> summary.descriptions().stream())
				.filter(Objects::nonNull)
				.map(String::strip)
				.filter(description -> !description.isEmpty())
				.findFirst()
				.orElse(null);
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
