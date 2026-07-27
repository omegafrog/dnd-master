package com.dndmaster.adventure.domain.knowledge;

import com.dndmaster.adventure.domain.adventure.SessionId;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public record SessionKnowledgeSet(SessionId sessionId, List<KnowledgeDocumentId> knowledgeDocumentIds) {
    public SessionKnowledgeSet {
        sessionId = Objects.requireNonNull(sessionId, "session id must not be null");
        knowledgeDocumentIds = List.copyOf(Objects.requireNonNull(knowledgeDocumentIds, "knowledge document ids must not be null"));
        var seen = new HashSet<KnowledgeDocumentId>();
        for (KnowledgeDocumentId knowledgeDocumentId : knowledgeDocumentIds) {
            Objects.requireNonNull(knowledgeDocumentId, "knowledge document ids must not contain null");
            if (!seen.add(knowledgeDocumentId)) {
                throw new IllegalArgumentException("knowledge document ids must be unique");
            }
        }
    }
}
