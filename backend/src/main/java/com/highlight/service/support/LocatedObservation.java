package com.highlight.service.support;

import com.highlight.entity.ObservationCategory;
import com.highlight.entity.ObservationKind;

/**
 * Model observation with offsets resolved against the book text; offsets are null when the quote was not found.
 */
public record LocatedObservation(ObservationKind kind, ObservationCategory category, String description, String quote,
								 Integer startOffset, Integer endOffset, Long chapterId, boolean explicitChange,
								 boolean inferred) {
}
