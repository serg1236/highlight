package com.highlight.ai;

import java.util.List;

/**
 * Model calls used by character extraction.
 */
public interface CharacterExtractionClient {

	/**
	 * @param chunkText       book excerpt with {@code [[CHAPTER n: title]]} markers
	 * @param knownCharacters characters found in earlier excerpts
	 */
	ExtractionResponse<ChunkExtraction> extractCharacters(String chunkText, List<RosterEntry> knownCharacters);

	ExtractionResponse<MergeDecision> mergeCharacters(List<MergeCandidate> candidates);
}
