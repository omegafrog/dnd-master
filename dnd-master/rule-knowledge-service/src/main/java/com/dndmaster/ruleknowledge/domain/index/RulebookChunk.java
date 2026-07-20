package com.dndmaster.ruleknowledge.domain.index;

import com.dndmaster.ruleknowledge.domain.rulebook.RulebookId;
import java.util.Objects;

public record RulebookChunk(
        RulebookId rulebookId,
        ChunkId chunkId,
        int sequence,
        ExtractedContentRange range,
        String content) {
    public RulebookChunk {
        Objects.requireNonNull(rulebookId, "rulebookId must not be null");
        Objects.requireNonNull(chunkId, "chunkId must not be null");
        Objects.requireNonNull(range, "range must not be null");
        if (sequence < 0) throw new IllegalArgumentException("sequence must not be negative");
        if (content == null || content.isEmpty()) throw new IllegalArgumentException("chunk content must not be empty");
        if (range.endExclusive() - range.startInclusive() != content.length()) {
            throw new IllegalArgumentException("chunk range must match content length");
        }
    }
}
