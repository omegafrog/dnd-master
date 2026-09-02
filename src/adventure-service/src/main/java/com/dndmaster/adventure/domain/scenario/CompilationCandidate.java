package com.dndmaster.adventure.domain.scenario;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Durable candidate-level evidence. Provider payloads stay outside this record. */
public record CompilationCandidate(
        UUID candidateId,
        UUID compilationId,
        String candidateKey,
        String candidateType,
        boolean required,
        CandidateCompleteness completeness,
        List<CandidateValidation> validations,
        CandidateRecoverability recoverability,
        int repairAttemptCount,
        String rawResolutionRef,
        String finalResolutionRef,
        Instant createdAt,
        Instant updatedAt) {
    public CompilationCandidate {
        candidateId = Objects.requireNonNull(candidateId, "candidate id must not be null");
        compilationId = Objects.requireNonNull(compilationId, "compilation id must not be null");
        if (candidateKey == null || candidateKey.isBlank()) throw new IllegalArgumentException("candidate key must not be blank");
        if (candidateType == null || candidateType.isBlank()) throw new IllegalArgumentException("candidate type must not be blank");
        completeness = Objects.requireNonNull(completeness, "completeness must not be null");
        validations = List.copyOf(Objects.requireNonNull(validations, "validations must not be null"));
        recoverability = Objects.requireNonNull(recoverability, "recoverability must not be null");
        if (repairAttemptCount < 0 || repairAttemptCount > 1) throw new IllegalArgumentException("repair attempts must be 0 or 1");
        createdAt = Objects.requireNonNull(createdAt, "created at must not be null");
        updatedAt = Objects.requireNonNull(updatedAt, "updated at must not be null");
    }

    public static CompilationCandidate of(UUID compilationId, String key, String type, boolean required,
            CandidateCompleteness completeness, List<CandidateValidation> validations,
            CandidateRecoverability recoverability, String rawRef, String finalRef) {
        return of(compilationId, key, type, required, completeness, validations, recoverability, 0, rawRef, finalRef);
    }

    public static CompilationCandidate of(UUID compilationId, String key, String type, boolean required,
            CandidateCompleteness completeness, List<CandidateValidation> validations,
            CandidateRecoverability recoverability, int repairAttemptCount, String rawRef, String finalRef) {
        Instant now = Instant.now();
        return new CompilationCandidate(UUID.randomUUID(), compilationId, key, type, required,
                completeness, validations, recoverability, repairAttemptCount, rawRef, finalRef, now, now);
    }
}
