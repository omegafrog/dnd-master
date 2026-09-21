package com.dndmaster.ruleknowledge.application.search;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class RrfFusionPolicyTest {
    @Test
    void matchesSharedDeterministicRrfContract() throws IOException {
        JsonNode contract = sharedContract().path("rrf");
        RrfFusionPolicy policy = new RrfFusionPolicy();

        List<RrfFusionPolicy.RankedChunk> results = policy.fuse(
                stringList(contract.path("dense")), stringList(contract.path("bm25")));

        assertEquals(contract.path("k").asInt(), RrfFusionPolicy.RANK_CONSTANT);
        assertEquals(contract.path("expected").size(), results.size());
        for (int index = 0; index < results.size(); index++) {
            JsonNode expected = contract.path("expected").get(index);
            RrfFusionPolicy.RankedChunk actual = results.get(index);
            assertEquals(expected.path("id").asText(), actual.stableChunkId());
            assertEquals(integerOrNull(expected, "dense_rank"), actual.denseRank());
            assertEquals(integerOrNull(expected, "bm25_rank"), actual.bm25Rank());
            assertEquals(expected.path("score").asDouble(), actual.rrfScore(), 0.0000000001);
        }
    }
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

    private static JsonNode sharedContract() throws IOException {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve("tests/fixtures/hybrid-retrieval-deterministic-contract.json");
            if (Files.isRegularFile(candidate)) return new ObjectMapper().readTree(candidate.toFile());
            current = current.getParent();
        }
        throw new IOException("shared deterministic retrieval contract was not found");
    }

    private static List<String> stringList(JsonNode values) {
        java.util.ArrayList<String> result = new java.util.ArrayList<>();
        values.forEach(value -> result.add(value.asText()));
        return result;
    }

    private static Integer integerOrNull(JsonNode value, String field) {
        return value.path(field).isNull() ? null : value.path(field).asInt();
    }
}
