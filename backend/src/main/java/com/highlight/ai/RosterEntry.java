package com.highlight.ai;

import java.util.List;

/**
 * A character found in earlier chunks, sent with later chunks so names stay consistent.
 */
public record RosterEntry(String name, List<String> aliases) {
}
