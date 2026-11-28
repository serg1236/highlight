package com.highlight.service.support;

import com.highlight.entity.BookLocation;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Locations of one book, and a lookup from every location name the scenes used (lower-cased) to its location.
 */
public record MergedLocations(List<BookLocation> locations, Map<String, BookLocation> byName) {

	public BookLocation find(String name) {
		return name == null ? null : byName.get(key(name));
	}

	static String key(String name) {
		return name.strip().toLowerCase(Locale.ROOT);
	}
}
