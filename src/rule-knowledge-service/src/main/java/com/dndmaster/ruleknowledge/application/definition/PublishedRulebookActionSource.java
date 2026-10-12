package com.dndmaster.ruleknowledge.application.definition;

import com.dndmaster.ruleknowledge.application.preprocessing.PreprocessingPageState;
import java.util.List;
import java.util.UUID;

public record PublishedRulebookActionSource(UUID documentId, String extractionVersion, long scopeVersion,
        List<PreprocessingPageState> preprocessedPages) {
    public PublishedRulebookActionSource {
        preprocessedPages = List.copyOf(preprocessedPages);
        if (scopeVersion <= 0) throw new IllegalArgumentException("scope version must be positive");
    }
}
