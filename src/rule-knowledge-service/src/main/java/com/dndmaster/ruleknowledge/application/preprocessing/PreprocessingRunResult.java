package com.dndmaster.ruleknowledge.application.preprocessing;

import java.util.List;
import java.util.regex.Pattern;

public record PreprocessingRunResult(
        String requestId,
        String versionId,
        String status,
        String sourceSha256,
        String policyVersion,
        List<PreprocessingPageState> pages,
        PreprocessingArtifactManifest artifacts) {

    public PreprocessingRunResult {
        requireText(requestId, "requestId");
        requireText(versionId, "versionId");
        requireText(status, "status");
        requireText(sourceSha256, "sourceSha256");
        requireText(policyVersion, "policyVersion");
        if (!Pattern.matches("[A-Za-z0-9._-]+", versionId) || ".".equals(versionId) || "..".equals(versionId)) {
            throw new IllegalArgumentException("versionId is unsafe");
        }
        pages = pages == null ? List.of() : List.copyOf(pages);
        if (artifacts == null) {
            throw new IllegalArgumentException("artifacts must not be null");
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
