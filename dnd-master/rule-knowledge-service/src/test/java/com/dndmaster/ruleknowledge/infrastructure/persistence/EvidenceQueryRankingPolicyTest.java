package com.dndmaster.ruleknowledge.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dndmaster.ruleknowledge.application.search.QueryIntent;
import com.dndmaster.ruleknowledge.domain.rulebook.DocumentType;
import org.junit.jupiter.api.Test;

class EvidenceQueryRankingPolicyTest {
    @Test
    void rules_and_stories_get_deterministic_type_priority() {
        assertEquals(0, EvidenceQueryRankingPolicy.priority(QueryIntent.RULE, DocumentType.RULEBOOK));
        assertEquals(1, EvidenceQueryRankingPolicy.priority(QueryIntent.RULE, DocumentType.STORYBOOK));
        assertEquals(1, EvidenceQueryRankingPolicy.priority(QueryIntent.STORY, DocumentType.RULEBOOK));
        assertEquals(0, EvidenceQueryRankingPolicy.priority(QueryIntent.STORY, DocumentType.STORYBOOK));
        assertEquals(0, EvidenceQueryRankingPolicy.priority(QueryIntent.MIXED, DocumentType.RULEBOOK));
        assertEquals(0, EvidenceQueryRankingPolicy.priority(QueryIntent.UNKNOWN, DocumentType.STORYBOOK));
    }
}
