package com.dndmaster.ruleknowledge.application.indexing;

import java.util.List;
import java.util.UUID;

public record PublishedRagChunkPage(long totalCount, int offset, List<Chunk> chunks) {
    public PublishedRagChunkPage { chunks = List.copyOf(chunks); }
    public record Chunk(UUID chunkId, int sequence, String content, String originalLocator,
            int pageNumber, List<String> sectionPath) {
        public Chunk { sectionPath = List.copyOf(sectionPath); }
    }
}
