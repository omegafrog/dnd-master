package com.dndmaster.adventure.application.scenario.compilation;

import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Adventure-side boundary for independently scoped character-context retrieval. */
public interface CharacterContextSearchPort {
    List<Evidence> search(Request request);

    record Request(UUID ownerId, List<DocumentScope> documents, String situation, int tokenBudget) {
        public Request {
            Objects.requireNonNull(ownerId, "owner id must not be null");
            documents = List.copyOf(Objects.requireNonNull(documents, "documents must not be null"));
            if (documents.isEmpty()) throw new IllegalArgumentException("documents must not be empty");
            if (situation == null || situation.isBlank()) throw new IllegalArgumentException("situation must not be blank");
            if (tokenBudget <= 0) throw new IllegalArgumentException("token budget must be positive");
        }
    }

    record DocumentScope(KnowledgeDocumentId documentId, String documentType, long extractionVersion) {
        public DocumentScope {
            Objects.requireNonNull(documentId, "document id must not be null");
            if (documentType == null || documentType.isBlank()) throw new IllegalArgumentException("document type must not be blank");
            if (extractionVersion < 0) throw new IllegalArgumentException("extraction version must not be negative");
        }
    }

    record Evidence(KnowledgeDocumentId documentId, String documentType, long extractionVersion,
                    String locator, String excerpt, double similarity) {
        public Evidence {
            Objects.requireNonNull(documentId, "document id must not be null");
            if (documentType == null || documentType.isBlank()) throw new IllegalArgumentException("document type must not be blank");
            if (extractionVersion < 0) throw new IllegalArgumentException("extraction version must not be negative");
            if (locator == null || locator.isBlank()) throw new IllegalArgumentException("locator must not be blank");
            if (excerpt == null || excerpt.isBlank()) throw new IllegalArgumentException("excerpt must not be blank");
            if (!Double.isFinite(similarity) || similarity < 0 || similarity > 1) throw new IllegalArgumentException("similarity must be between zero and one");
        }
    }
}
