package com.dndmaster.ruleknowledge.application.preprocessing;

import java.nio.file.Path;
import java.util.Map;

/** Artifact identity after absolute process paths have been removed. */
public record PreprocessingArtifactManifest(
        String manifestSha256,
        Map<String, String> artifactSha256,
        Map<String, Path> artifactPaths) {

    public PreprocessingArtifactManifest(String manifestSha256, Map<String, String> artifactSha256) {
        this(manifestSha256, artifactSha256, Map.of());
    }

    public PreprocessingArtifactManifest {
        if (manifestSha256 == null || manifestSha256.isBlank()) {
            throw new IllegalArgumentException("manifest hash must not be blank");
        }
        artifactSha256 = artifactSha256 == null ? Map.of() : Map.copyOf(artifactSha256);
        artifactPaths = artifactPaths == null ? Map.of() : artifactPaths.entrySet().stream()
                .filter(entry -> entry.getKey() != null && entry.getValue() != null)
                .collect(java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> entry.getValue().toAbsolutePath().normalize()));
        artifactSha256.forEach((name, hash) -> {
            if (name == null || name.isBlank() || hash == null || hash.isBlank()) {
                throw new IllegalArgumentException("artifact identity must not be blank");
            }
        });
    }

    public Path artifactPath(String name) {
        return artifactPaths.get(name);
    }
}
