package com.dndmaster.ruleknowledge.domain.document.chunk;

import java.util.List;

public record RagChunk(String id, String content, List<String> canonicalPath, List<String> sourceNodeIds,
                       int firstPage, int lastPage, double hierarchyConfidence) {
    public RagChunk {
        if (id == null || id.isBlank() || content == null || content.isBlank()) throw new IllegalArgumentException("chunk id and content required");
        canonicalPath = canonicalPath == null ? List.of() : List.copyOf(canonicalPath);
        sourceNodeIds = sourceNodeIds == null ? List.of() : List.copyOf(sourceNodeIds);
        if (firstPage < 1 || lastPage < firstPage || !Double.isFinite(hierarchyConfidence)) throw new IllegalArgumentException("invalid chunk metadata");
    }
    public String embeddingText() { return canonicalPath.isEmpty() ? content : String.join(" > ", canonicalPath) + "\n\n" + content; }
}
