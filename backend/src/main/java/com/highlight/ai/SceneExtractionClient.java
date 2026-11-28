package com.highlight.ai;

import java.util.List;

/**
 * Model calls used by scene and setting extraction.
 */
public interface SceneExtractionClient {

	/**
	 * @param chunkText       book excerpt with {@code [[CHAPTER n: title]]} markers
	 * @param knownCharacters characters found in earlier excerpts
	 * @param knownLocations  location names used in earlier excerpts
	 */
	ExtractionResponse<ChunkScenes> extractScenes(String chunkText, List<RosterEntry> knownCharacters,
												  List<String> knownLocations);

	ExtractionResponse<WorldSynthesis> synthesizeWorld(WorldSynthesisInput input);
}
