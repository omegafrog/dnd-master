package com.dndmaster.gmeval.tuning;

import com.dndmaster.gmeval.registry.PromptRole;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/** Reproducible before/after evidence carried into role activation and rollback. */
public record TuningLineageDeltaReport(String evaluationId, String proposalId, PromptRole role,
                                       String artifactId, String baseModelVersion, String optimizedPromptVersion,
                                       String tunedModelVersion, TuningMetricsDelta evaluationDelta,
                                       TuningMetricsDelta holdoutDelta, String fingerprint) {
    public TuningLineageDeltaReport {
        evaluationId = required(evaluationId, "evaluation id");
        proposalId = required(proposalId, "proposal id");
        role = Objects.requireNonNull(role, "lineage role required");
        artifactId = required(artifactId, "artifact id");
        baseModelVersion = required(baseModelVersion, "base model version");
        optimizedPromptVersion = required(optimizedPromptVersion, "optimized prompt version");
        tunedModelVersion = required(tunedModelVersion, "tuned model version");
        evaluationDelta = Objects.requireNonNull(evaluationDelta, "evaluation delta required");
        holdoutDelta = Objects.requireNonNull(holdoutDelta, "holdout delta required");
        fingerprint = fingerprint == null || fingerprint.isBlank() ? fingerprint(evaluationId, proposalId, role,
                artifactId, baseModelVersion, optimizedPromptVersion, tunedModelVersion, evaluationDelta, holdoutDelta) : fingerprint;
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " required");
        return value.trim();
    }

    private static String fingerprint(Object... values) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(java.util.Arrays.deepToString(values).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }
}
