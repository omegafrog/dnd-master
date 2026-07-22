package com.dndmaster.ruleknowledge.infrastructure.persistence;

import com.dndmaster.ruleknowledge.application.search.QueryIntent;
import com.dndmaster.ruleknowledge.domain.rulebook.DocumentType;

final class EvidenceQueryRankingPolicy {
    private EvidenceQueryRankingPolicy() {}

    static int priority(QueryIntent queryIntent, DocumentType documentType) {
        return switch (queryIntent) {
            case RULE -> documentType == DocumentType.RULEBOOK ? 0 : 1;
            case STORY -> documentType == DocumentType.STORYBOOK ? 0 : 1;
            case MIXED, UNKNOWN -> 0;
        };
    }
}
