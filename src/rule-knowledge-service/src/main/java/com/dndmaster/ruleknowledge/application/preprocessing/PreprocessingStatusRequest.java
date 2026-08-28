package com.dndmaster.ruleknowledge.application.preprocessing;

import java.nio.file.Path;

public record PreprocessingStatusRequest(
        String requestId,
        String versionId,
        Path artifactRoot) {

    public PreprocessingStatusRequest {
        if (requestId == null || requestId.isBlank()) {
            throw new IllegalArgumentException("requestId must not be blank");
        }
        if (versionId == null || versionId.isBlank()) {
            throw new IllegalArgumentException("versionId must not be blank");
        }
        if (artifactRoot == null) {
            throw new IllegalArgumentException("artifactRoot must not be null");
        }
    }
}
