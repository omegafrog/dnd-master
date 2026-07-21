package com.dndmaster.ruleknowledge.application.indexing;

import com.dndmaster.ruleknowledge.domain.index.RulebookChunk;
import java.util.List;

public interface EmbeddingPort {
    List<ChunkEmbedding> embed(List<RulebookChunk> chunks, String embeddingModel, int expectedDimension);
}
