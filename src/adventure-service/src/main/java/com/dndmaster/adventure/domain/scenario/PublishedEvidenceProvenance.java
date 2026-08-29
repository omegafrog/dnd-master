package com.dndmaster.adventure.domain.scenario;

import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import java.util.List;
import java.util.Objects;

/** Immutable locator returned only by the published RAG evidence contract. */
public record PublishedEvidenceProvenance(
        KnowledgeDocumentId documentId,
        long extractionVersion,
        int pageNumber,
        List<String> sectionPath,
        List<Double> bbox,
        String tableCell,
        String locator) {
    public PublishedEvidenceProvenance {
        documentId = Objects.requireNonNull(documentId, "document id must not be null");
        if (extractionVersion <= 0) throw new IllegalArgumentException("extraction version must be positive");
        if (pageNumber <= 0) throw new IllegalArgumentException("page number must be positive");
        sectionPath = sectionPath == null ? List.of() : sectionPath.stream()
                .filter(item -> item != null && !item.isBlank()).map(String::trim).toList();
        bbox = bbox == null ? List.of() : List.copyOf(bbox);
        if (!bbox.isEmpty() && bbox.size() != 4) throw new IllegalArgumentException("bbox must contain four coordinates");
        if (bbox.stream().anyMatch(value -> value == null || !Double.isFinite(value))) {
            throw new IllegalArgumentException("bbox values must be finite");
        }
        if (tableCell != null && tableCell.isBlank()) tableCell = null;
        if (locator == null || locator.isBlank()) throw new IllegalArgumentException("locator must not be blank");
        locator = locator.trim();
    }
}
