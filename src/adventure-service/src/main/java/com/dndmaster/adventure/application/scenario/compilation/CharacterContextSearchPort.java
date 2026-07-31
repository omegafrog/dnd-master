package com.dndmaster.adventure.application.scenario.compilation;

import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Adventure-side boundary for independently scoped character-context retrieval. */
public interface CharacterContextSearchPort {
    List<Evidence> search(Request request);

    final class CharacterContextSearchException extends RuntimeException {
        public CharacterContextSearchException(String message, Throwable cause) { super(message, cause); }
        public CharacterContextSearchException(String message) { super(message); }
    }

    record Request(UUID ownerId, List<DocumentScope> documents, String situation,
                   java.util.Map<String, Double> thresholds, int tokenBudget) {
        public Request {
            Objects.requireNonNull(ownerId, "owner id must not be null");
            documents = List.copyOf(Objects.requireNonNull(documents, "documents must not be null"));
            if (documents.isEmpty()) throw new IllegalArgumentException("documents must not be empty");
            if (situation == null || situation.isBlank()) throw new IllegalArgumentException("situation must not be blank");
            java.util.Map<String, Double> requestedThresholds =
                    java.util.Map.copyOf(Objects.requireNonNull(thresholds, "thresholds must not be null"));
            requestedThresholds.forEach((type, value) -> {
                if (type == null || type.isBlank() || value == null || !Double.isFinite(value) || value < 0 || value > 1) {
                    throw new IllegalArgumentException("thresholds must contain valid document type values");
                }
            });
            java.util.Set<String> documentTypes = documents.stream()
                    .map(DocumentScope::documentType)
                    .collect(java.util.stream.Collectors.toSet());
            thresholds = requestedThresholds.entrySet().stream()
                    .filter(entry -> documentTypes.contains(entry.getKey().toUpperCase(java.util.Locale.ROOT)))
                    .collect(java.util.stream.Collectors.toUnmodifiableMap(
                            entry -> entry.getKey().toUpperCase(java.util.Locale.ROOT),
                            java.util.Map.Entry::getValue));
            if (thresholds.isEmpty()) {
                thresholds = documentTypes.stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                        type -> type, ignored -> 0.25d));
            }
            if (tokenBudget < 0) throw new IllegalArgumentException("token budget must not be negative");
        }
    }

    record DocumentScope(KnowledgeDocumentId documentId, String documentType, long extractionVersion) {
        public DocumentScope {
            Objects.requireNonNull(documentId, "document id must not be null");
            documentType = Objects.requireNonNull(documentType, "document type must not be null").trim().toUpperCase(java.util.Locale.ROOT);
            if (documentType.isBlank()) throw new IllegalArgumentException("document type must not be blank");
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
