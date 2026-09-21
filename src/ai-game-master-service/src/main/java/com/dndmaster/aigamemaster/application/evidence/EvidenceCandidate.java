package com.dndmaster.aigamemaster.application.evidence;

import java.util.Objects;

/** Candidate content supplied to an internal evidence-model operation. */
public record EvidenceCandidate(String evidenceId, String documentType, String locator, String excerpt) {
    public EvidenceCandidate {
        evidenceId = required(evidenceId, "evidenceId");
        documentType = required(documentType, "documentType");
        locator = required(locator, "locator");
        excerpt = required(excerpt, "excerpt");
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value;
    }
}
