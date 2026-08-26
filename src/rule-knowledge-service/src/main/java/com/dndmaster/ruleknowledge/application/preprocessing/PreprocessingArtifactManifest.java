package com.dndmaster.ruleknowledge.application.preprocessing;

import java.util.Map;
import java.util.Objects;

/** Artifact identity after absolute process paths have been removed. */
public record PreprocessingArtifactManifest(
        String manifestSha256,
        Map<String, String> artifactSha256) {

    public PreprocessingArtifactManifest {
        if (manifestSha256 == null || manifestSha256.isBlank()) {
            throw new IllegalArgumentException("manifest hash must not be blank");
        }
        artifactSha256 = artifactSha256 == null ? Map.of() : Map.copyOf(artifactSha256);
        artifactSha256.forEach((name, hash) -> {
            if (name == null || name.isBlank() || hash == null || hash.isBlank()) {
                throw new IllegalArgumentException("artifact identity must not be blank");
            }
        });
    }
}
