package com.dndmaster.ruleknowledge.application.publication;

import com.dndmaster.ruleknowledge.domain.rulebook.RulebookId;

public record RagExtractionVersion(
        RulebookId documentId,
        String extractionVersion,
        ExtractionPublicationStatus status) {}
