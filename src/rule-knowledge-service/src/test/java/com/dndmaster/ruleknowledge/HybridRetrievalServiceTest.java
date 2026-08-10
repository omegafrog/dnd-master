package com.dndmaster.ruleknowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dndmaster.ruleknowledge.application.retrieval.HybridRetrievalService;
import com.dndmaster.ruleknowledge.application.retrieval.HybridSearchCandidate;
import com.dndmaster.ruleknowledge.application.search.QueryIntent;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HybridRetrievalServiceTest {
    @Test
    void reranksDenseAndLexicalCandidatesWithIntentWeightsAndLimit() {
        UUID dense = UUID.randomUUID();
        UUID lexical = UUID.randomUUID();
        var result = new HybridRetrievalService().rerank(List.of(
                new HybridSearchCandidate(dense, 1, 0, 0),
                new HybridSearchCandidate(lexical, 0, 1, 0)), QueryIntent.RULE, 1);

        assertEquals(dense, result.getFirst().evidenceId());
        assertEquals(1, result.size());
    }
}
