package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import java.util.Objects;

// 검색이나 계획에 쓸 수 있는 단일 근거 조각이다.
public record RuntimeEvidence(
        RuntimeEvidenceType evidenceType,
        KnowledgeDocumentId knowledgeDocumentId,
        long extractionVersion,
        String locator,
        String excerpt,
        StoryEvidenceVisibility visibility,
        String disclosureEvent,
        long disclosureTurn) {
    public RuntimeEvidence(RuntimeEvidenceType evidenceType, KnowledgeDocumentId knowledgeDocumentId,
            long extractionVersion, String locator, String excerpt) {
        this(evidenceType, knowledgeDocumentId, extractionVersion, locator, excerpt,
                evidenceType == RuntimeEvidenceType.STORYBOOK ? StoryEvidenceVisibility.GM_ONLY : StoryEvidenceVisibility.PLAYER_VISIBLE,
                null, 0);
    }
    public RuntimeEvidence {
        evidenceType = Objects.requireNonNull(evidenceType, "evidence type must not be null");
        knowledgeDocumentId = Objects.requireNonNull(knowledgeDocumentId, "knowledge document id must not be null");
        if (extractionVersion <= 0) {
            throw new IllegalArgumentException("extraction version must be positive");
        }
        locator = required(locator, "locator");
        excerpt = required(excerpt, "excerpt");
        visibility = visibility == null
                ? (evidenceType == RuntimeEvidenceType.STORYBOOK ? StoryEvidenceVisibility.GM_ONLY : StoryEvidenceVisibility.PLAYER_VISIBLE)
                : visibility;
        if (disclosureTurn < 0) throw new IllegalArgumentException("disclosure turn must not be negative");
        if (visibility == StoryEvidenceVisibility.REVEALED_AFTER_EVENT
                && (disclosureEvent == null || disclosureEvent.isBlank())) {
            throw new IllegalArgumentException("reveal event required");
        }
        disclosureEvent = disclosureEvent == null ? null : disclosureEvent.trim();
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }
}
