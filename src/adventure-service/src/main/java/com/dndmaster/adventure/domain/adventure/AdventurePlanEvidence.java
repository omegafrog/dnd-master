package com.dndmaster.adventure.domain.adventure;

import java.util.Objects;
import java.util.UUID;
import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import com.dndmaster.adventure.domain.scenario.PublishedEvidenceProvenance;

public record AdventurePlanEvidence(String documentType, UUID documentId, long extractionVersion,
        String locator, String quote, double confidence, PublishedEvidenceProvenance provenance) {
    public AdventurePlanEvidence(String documentType, UUID documentId, long extractionVersion,
            String locator, String quote, double confidence) {
        this(documentType, documentId, extractionVersion, locator, quote, confidence,
                new PublishedEvidenceProvenance(new KnowledgeDocumentId(documentId), extractionVersion,
                        pageNumber(locator), java.util.List.of(), java.util.List.of(), null, locator));
    }

    public AdventurePlanEvidence {
        documentType = required(documentType, "document type");
        documentId = Objects.requireNonNull(documentId, "document id must not be null");
        locator = required(locator, "evidence locator");
        quote = required(quote, "evidence quote");
        if (extractionVersion <= 0) throw new IllegalArgumentException("extraction version must be positive");
        if (confidence < 0 || confidence > 1) throw new IllegalArgumentException("confidence must be between 0 and 1");
        provenance = Objects.requireNonNull(provenance, "evidence provenance must not be null");
        if (!documentId.equals(provenance.documentId().value())
                || extractionVersion != provenance.extractionVersion()
                || !locator.equals(provenance.locator())) {
            throw new IllegalArgumentException("evidence provenance identity does not match citation");
        }
    }

    private static int pageNumber(String locator) {
        if (locator == null) return 1;
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(?i)page(?:=|:|\\s+)(\\d+)").matcher(locator);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : 1;
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }
}
