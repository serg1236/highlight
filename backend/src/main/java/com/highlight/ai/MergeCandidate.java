package com.highlight.ai;

import com.highlight.entity.CharacterSex;

import java.util.List;

/**
 * One per-chunk character, summarized for the merge call.
 */
public record MergeCandidate(int id, String name, List<String> aliases, CharacterSex sex,
							 List<String> traits) {
}
