package com.dndmaster.aigamemaster.benchmark.rag;

import java.util.Objects;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Blind post-response rating; contains provenance, never prompt or response text. */
public record RagAbReviewerRecord(String caseId, RagAbCondition condition, int repetition,
                                  String reviewerId, double score, String reviewSessionId,
                                  String responseHash) {
    public RagAbReviewerRecord {
        caseId = required(caseId, "caseId");
        condition = Objects.requireNonNull(condition, "condition");
        reviewerId = required(reviewerId, "reviewerId");
        reviewSessionId = required(reviewSessionId, "reviewSessionId");
        responseHash = required(responseHash, "responseHash");
        if (repetition < 0) throw new IllegalArgumentException("repetition must be non-negative");
        if (!Double.isFinite(score) || score < 1 || score > 5) {
            throw new IllegalArgumentException("review score must be finite and 1..5");
        }
    }

    public static String sha256(String response) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(response.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (Exception failure) {
            throw new IllegalStateException("SHA-256 unavailable", failure);
        }
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " required");
        return value.trim();
    }
}
