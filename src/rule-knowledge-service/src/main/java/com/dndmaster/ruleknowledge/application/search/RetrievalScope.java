package com.dndmaster.ruleknowledge.application.search;

import com.dndmaster.ruleknowledge.domain.rulebook.DocumentType;
import com.dndmaster.ruleknowledge.domain.rulebook.KnowledgeDocumentId;
import java.util.*;

public record RetrievalScope(
        UUID ownerId,
        String sessionId,
        String packageId,
        Map<KnowledgeDocumentId, DocumentType> documents,
        Map<KnowledgeDocumentId, Long> extractionVersions,
        Set<String> allowedVisibility,
        String currentStage,
        Set<String> activeLocators) {
    public RetrievalScope {
        Objects.requireNonNull(ownerId, "owner id must not be null");
        documents = Map.copyOf(Objects.requireNonNull(documents, "documents must not be null"));
        extractionVersions = Map.copyOf(Objects.requireNonNull(extractionVersions, "extraction versions must not be null"));
        allowedVisibility = Set.copyOf(Objects.requireNonNull(allowedVisibility, "allowed visibility must not be null"));
        activeLocators = Set.copyOf(Objects.requireNonNull(activeLocators, "active locators must not be null"));
    }

    public boolean accepts(HybridRetrievalCandidate candidate) {
        return ownerId.equals(candidate.ownerId())
                && (sessionId == null || sessionId.equals(candidate.sessionId()))
                && (packageId == null || packageId.equals(candidate.packageId()))
                && documents.getOrDefault(candidate.documentId(), null) == candidate.documentType()
                && Objects.equals(extractionVersions.get(candidate.documentId()), candidate.extractionVersion())
                && allowedVisibility.contains(candidate.visibility())
                && (currentStage == null || currentStage.equals(candidate.stage()))
                && (activeLocators.isEmpty() || activeLocators.contains(candidate.locator()));
    }

    public static Builder builder(UUID ownerId) { return new Builder(ownerId); }

    public static final class Builder {
        private final UUID ownerId;
        private String sessionId;
        private String packageId;
        private final Map<KnowledgeDocumentId, DocumentType> documents = new LinkedHashMap<>();
        private final Map<KnowledgeDocumentId, Long> versions = new LinkedHashMap<>();
        private final Set<String> visibility = new LinkedHashSet<>(Set.of("PLAYER_VISIBLE", "PUBLIC_SUMMARY", "DISCOVERED"));
        private String stage;
        private final Set<String> locators = new LinkedHashSet<>();
        private Builder(UUID ownerId) { this.ownerId = ownerId; }
        public Builder sessionId(String value) { sessionId = value; return this; }
        public Builder packageId(String value) { packageId = value; return this; }
        public Builder document(KnowledgeDocumentId id, DocumentType type, long version) {
            documents.put(id, type); versions.put(id, version); return this;
        }
        public Builder visibleToPlayer() { visibility.clear(); visibility.add("PLAYER_VISIBLE"); return this; }
        public Builder allowedVisibility(String value) { visibility.add(value); return this; }
        public Builder stage(String value) { stage = value; return this; }
        public Builder activeLocator(String value) { locators.add(value); return this; }
        public RetrievalScope build() { return new RetrievalScope(ownerId, sessionId, packageId, documents, versions, visibility, stage, locators); }
    }
}
