package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import com.dndmaster.adventure.domain.scenario.ScenarioPackage;
import java.util.List;
import java.util.Objects;

/** Searches the published story material for the source of the first playable situation. */
@FunctionalInterface
public interface OpeningSourceContextSearchPort {
    List<Result> search(OwnerPlayerId ownerPlayerId, ScenarioPackage scenarioPackage);

    record Result(
            KnowledgeDocumentId knowledgeDocumentId,
            long extractionVersion,
            String locator,
            String excerpt,
            double score) {
        public Result {
            knowledgeDocumentId = Objects.requireNonNull(knowledgeDocumentId, "knowledge document id must not be null");
            if (extractionVersion <= 0) throw new IllegalArgumentException("extraction version must be positive");
            locator = required(locator, "locator");
            excerpt = required(excerpt, "excerpt");
            if (!Double.isFinite(score) || score < 0d) throw new IllegalArgumentException("score must be finite and non-negative");
        }

        private static String required(String value, String name) {
            if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
            return value.trim();
        }
    }
}
