package com.dndmaster.ruleknowledge.application.preprocessing;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public record PreprocessingRetryRequest(
        String requestId,
        String versionId,
        Path artifactRoot,
        List<Integer> pages,
        Map<Integer, Map<String, Integer>> layoutSelections) {

    public PreprocessingRetryRequest {
        if (requestId == null || requestId.isBlank()) {
            throw new IllegalArgumentException("requestId must not be blank");
        }
        if (versionId == null || versionId.isBlank()) {
            throw new IllegalArgumentException("versionId must not be blank");
        }
        if (artifactRoot == null) {
            throw new IllegalArgumentException("artifactRoot must not be null");
        }
        if (pages == null || pages.isEmpty() || pages.stream().anyMatch(page -> page == null || page < 1)) {
            throw new IllegalArgumentException("pages must contain positive page numbers");
        }
        pages = List.copyOf(pages);
        layoutSelections = layoutSelections == null ? Map.of() : Map.copyOf(layoutSelections);
    }

    public PreprocessingRetryRequest(String requestId, String versionId, Path artifactRoot, List<Integer> pages) {
        this(requestId, versionId, artifactRoot, pages, Map.of());
    }
}
