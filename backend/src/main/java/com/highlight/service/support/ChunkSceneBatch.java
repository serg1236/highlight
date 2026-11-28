package com.highlight.service.support;

import com.highlight.ai.ExtractedScene;

import java.util.List;

/**
 * Scenes the model reported for one chunk, in reading order.
 */
public record ChunkSceneBatch(BookChunk chunk, List<ExtractedScene> scenes) {
}
