package com.dndmaster.adventure.domain.adventure;

import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import java.util.Set;
import java.util.UUID;

public record RetrievalScope(Set<KnowledgeDocumentId> documentIds, Set<UUID> sessionIds, int maxCalls) {
    public RetrievalScope {
        documentIds = documentIds == null ? Set.of() : Set.copyOf(documentIds);
        sessionIds = sessionIds == null ? Set.of() : Set.copyOf(sessionIds);
        if (maxCalls < 1 || maxCalls > 3) throw new IllegalArgumentException("judge retrieval calls must be between 1 and 3");
    }
    public boolean allows(KnowledgeDocumentId id) { return documentIds.contains(id); }
}
