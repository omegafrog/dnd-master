package com.dndmaster.aigamemaster.benchmark.finetuning;

import com.dndmaster.aigamemaster.infrastructure.ai.GmProviderRequest;
import java.util.Objects;

/** Provider-neutral model identity. Digest binds report evidence to the evaluated artifact. */
public record FineTuningModelArtifact(Variant variant, GmProviderRequest provider,
                                      String artifactDigest, String splitVersion, String trainingDigest) {
    public enum Variant { BASE, FINE_TUNED }

    public FineTuningModelArtifact {
        Objects.requireNonNull(variant, "variant");
        Objects.requireNonNull(provider, "provider");
        artifactDigest = required(artifactDigest, "artifact digest");
        splitVersion = required(splitVersion, "split version");
        trainingDigest = required(trainingDigest, "training digest");
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " required");
        return value.trim();
    }
}
