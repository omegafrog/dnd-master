package com.dndmaster.ruleknowledge.application.search;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class RrfFusionPolicyTest {
    @Test
    void combinesRanksDeduplicatesStableChunkIdsAndBreaksTiesByStableId() {
        RrfFusionPolicy policy = new RrfFusionPolicy();

        List<RrfFusionPolicy.RankedChunk> results = policy.fuse(
                List.of("chunk-b", "chunk-a", "chunk-c"),
                List.of("chunk-a", "chunk-b", "chunk-d"));

        assertEquals(List.of("chunk-a", "chunk-b", "chunk-c", "chunk-d"),
                results.stream().map(RrfFusionPolicy.RankedChunk::stableChunkId).toList());
        assertEquals(2, results.get(0).denseRank());
        assertEquals(1, results.get(0).bm25Rank());
        assertEquals(1, results.get(1).denseRank());
        assertEquals(2, results.get(1).bm25Rank());
        assertEquals(1.0 / 61.0 + 1.0 / 62.0, results.get(0).rrfScore(), 0.0000001);
    }

    @Test
    void capsEachInputAtThirtyAndTheMergedCandidatePoolAtSixty() {
        RrfFusionPolicy policy = new RrfFusionPolicy();
        List<String> dense = java.util.stream.IntStream.range(0, 31).mapToObj(i -> "dense-" + i).toList();
        List<String> bm25 = java.util.stream.IntStream.range(0, 31).mapToObj(i -> "bm25-" + i).toList();

        List<RrfFusionPolicy.RankedChunk> results = policy.fuse(dense, bm25);

        assertEquals(60, results.size());
        assertEquals("bm25-0", results.get(0).stableChunkId());
        assertEquals(false, results.stream().map(RrfFusionPolicy.RankedChunk::stableChunkId).anyMatch("dense-30"::equals));
        assertEquals(false, results.stream().map(RrfFusionPolicy.RankedChunk::stableChunkId).anyMatch("bm25-30"::equals));
    }
}
