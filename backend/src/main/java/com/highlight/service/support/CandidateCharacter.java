package com.highlight.service.support;

import com.highlight.entity.CharacterSex;

import java.util.List;

/**
 * Character as extracted from one chunk, before merging across chunks.
 */
public record CandidateCharacter(int id, String name, List<String> aliases, CharacterSex sex,
								 List<LocatedObservation> observations) {
}
