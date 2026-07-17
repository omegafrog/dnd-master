package com.dndmaster.ruleknowledge.domain.index;

import com.dndmaster.ruleknowledge.domain.rulebook.Rulebook;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class RulebookIndexingPolicy {
    public static final long AUTOMATIC_SPLIT_THRESHOLD_BYTES = 100L * 1024 * 1024;

    private final int maximumChunkCharacters;

    public RulebookIndexingPolicy(int maximumChunkCharacters) {
        if (maximumChunkCharacters <= 0) throw new IllegalArgumentException("maximum chunk size must be positive");
        this.maximumChunkCharacters = maximumChunkCharacters;
    }

    public RulebookIndex createIndex(Rulebook rulebook, IndexKey key, int dimension) {
        requireEligible(rulebook);
        if (!rulebook.id().equals(key.rulebookId())) {
            throw new IllegalArgumentException("index key must reference the rulebook");
        }
        return new RulebookIndex(IndexId.generate(), key, rulebook.ownerPlayerId(), dimension);
    }

    public List<RulebookChunk> createChunks(Rulebook rulebook) {
        requireEligible(rulebook);
        String content = rulebook.extractionResult().orElseThrow().content().orElseThrow();
        int chunkSize = maximumChunkCharacters;
        if (requiresAutomaticSplit(rulebook) && content.length() <= maximumChunkCharacters) {
            if (content.length() < 2) {
                throw new IllegalStateException("oversized rulebook needs enough extracted content for automatic splitting");
            }
            chunkSize = (content.length() + 1) / 2;
        }

        List<RulebookChunk> chunks = new ArrayList<>();
        for (int start = 0, sequence = 0; start < content.length(); start += chunkSize, sequence++) {
            int end = Math.min(content.length(), start + chunkSize);
            String chunkContent = content.substring(start, end);
            UUID chunkUuid = UUID.nameUUIDFromBytes(
                    (rulebook.id().value() + ":" + sequence).getBytes(StandardCharsets.UTF_8));
            chunks.add(new RulebookChunk(
                    rulebook.id(),
                    new ChunkId(chunkUuid),
                    sequence,
                    new ExtractedContentRange(start, end),
                    chunkContent));
        }
        return List.copyOf(chunks);
    }

    public boolean requiresAutomaticSplit(Rulebook rulebook) {
        return Objects.requireNonNull(rulebook, "rulebook must not be null").fileSize().bytes()
                > AUTOMATIC_SPLIT_THRESHOLD_BYTES;
    }

    private static void requireEligible(Rulebook rulebook) {
        if (!Objects.requireNonNull(rulebook, "rulebook must not be null").isEligibleForSplitting()) {
            throw new IllegalStateException("rulebook is not eligible for indexing");
        }
    }
}
