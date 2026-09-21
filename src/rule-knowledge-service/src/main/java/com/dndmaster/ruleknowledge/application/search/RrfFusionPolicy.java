package com.dndmaster.ruleknowledge.application.search;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Deterministic reciprocal-rank fusion for the two document-knowledge candidate lists. */
public final class RrfFusionPolicy {
    public static final int RANK_CONSTANT = 60;
    public static final int MAX_RESULTS_PER_RETRIEVER = 30;
    public static final int MAX_UNIQUE_CANDIDATES = 60;

    public List<RankedChunk> fuse(List<String> denseStableChunkIds, List<String> bm25StableChunkIds) {
        Map<String, MutableRankedChunk> byId = new LinkedHashMap<>();
        addRanks(byId, denseStableChunkIds, true);
        addRanks(byId, bm25StableChunkIds, false);
        return byId.values().stream()
                .map(MutableRankedChunk::toValue)
                .sorted(Comparator.comparingDouble(RankedChunk::rrfScore).reversed()
                        .thenComparing(RankedChunk::stableChunkId))
                .limit(MAX_UNIQUE_CANDIDATES)
                .toList();
    }

    private static void addRanks(Map<String, MutableRankedChunk> byId, List<String> values, boolean dense) {
        Objects.requireNonNull(values, "candidate ids must not be null");
        for (int offset = 0; offset < Math.min(values.size(), MAX_RESULTS_PER_RETRIEVER); offset++) {
            String id = Objects.requireNonNull(values.get(offset), "candidate id must not be null");
            if (id.isBlank()) throw new IllegalArgumentException("candidate id must not be blank");
            int rank = offset + 1;
            MutableRankedChunk candidate = byId.computeIfAbsent(id, MutableRankedChunk::new);
            if (dense && candidate.denseRank == null) candidate.denseRank = rank;
            if (!dense && candidate.bm25Rank == null) candidate.bm25Rank = rank;
        }
    }

    public record RankedChunk(String stableChunkId, Integer denseRank, Integer bm25Rank, double rrfScore) { }

    private static final class MutableRankedChunk {
        private final String stableChunkId;
        private Integer denseRank;
        private Integer bm25Rank;

        private MutableRankedChunk(String stableChunkId) { this.stableChunkId = stableChunkId; }

        private RankedChunk toValue() {
            double score = contribution(denseRank) + contribution(bm25Rank);
            return new RankedChunk(stableChunkId, denseRank, bm25Rank, score);
        }

        private static double contribution(Integer rank) {
            return rank == null ? 0.0 : 1.0 / (RANK_CONSTANT + rank);
        }
    }
}
