package com.dndmaster.ruleknowledge.application.preprocessing;

import java.nio.file.Path;
import java.util.List;

public record PreprocessingRetryRequest(
        String requestId,
        String versionId,
        Path artifactRoot,
        List<Integer> pages) {

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
    }
}
