package com.dndmaster.ruleknowledge.application.preprocessing;

import java.nio.file.Path;
import java.util.Objects;

public record PreprocessingRunRequest(
        String requestId,
        Path sourcePath,
        String sourceSha256,
        String policyVersion,
        Path outputDir,
        String versionId) {

    public PreprocessingRunRequest {
        requireText(requestId, "requestId");
        Objects.requireNonNull(sourcePath, "sourcePath must not be null");
        requireText(sourceSha256, "sourceSha256");
        requireText(policyVersion, "policyVersion");
        Objects.requireNonNull(outputDir, "outputDir must not be null");
        if (versionId != null && versionId.isBlank()) {
            throw new IllegalArgumentException("versionId must not be blank");
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
