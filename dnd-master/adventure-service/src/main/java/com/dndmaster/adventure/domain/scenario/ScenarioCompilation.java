package com.dndmaster.adventure.domain.scenario;

import java.util.Objects;
import java.util.UUID;

public final class ScenarioCompilation {
    private final UUID id;
    private final ScenarioBundleId bundleId;
    private final long bundleRevision;
    private final String inputFingerprint;
    private final ScenarioCompilationStatus status;
    private final int attempt;
    private final UUID leaseToken;
    private final UUID packageId;
    private final String failureReason;

    private ScenarioCompilation(
            UUID id, ScenarioBundleId bundleId, long bundleRevision, String inputFingerprint,
            ScenarioCompilationStatus status, int attempt, UUID leaseToken, UUID packageId, String failureReason) {
        this.id = Objects.requireNonNull(id, "compilation id must not be null");
        this.bundleId = Objects.requireNonNull(bundleId, "bundle id must not be null");
        this.inputFingerprint = Objects.requireNonNull(inputFingerprint, "input fingerprint must not be null");
        if (bundleRevision <= 0 || inputFingerprint.isBlank() || attempt < 0) {
            throw new IllegalArgumentException("invalid compilation identity or attempt");
        }
        this.bundleRevision = bundleRevision;
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.attempt = attempt;
        this.leaseToken = leaseToken;
        this.packageId = packageId;
        this.failureReason = failureReason;
    }

    public static ScenarioCompilation request(ScenarioBundleId bundleId, long bundleRevision, String inputFingerprint) {
        return new ScenarioCompilation(UUID.randomUUID(), bundleId, bundleRevision, inputFingerprint,
                ScenarioCompilationStatus.REQUESTED, 0, null, null, null);
    }

    public static ScenarioCompilation rehydrate(
            UUID id, ScenarioBundleId bundleId, long bundleRevision, String inputFingerprint,
            ScenarioCompilationStatus status, int attempt, UUID leaseToken, UUID packageId, String failureReason) {
        return new ScenarioCompilation(id, bundleId, bundleRevision, inputFingerprint, status, attempt,
                leaseToken, packageId, failureReason);
    }

    public ScenarioCompilation claim(UUID deliveryToken) {
        requireStatus(ScenarioCompilationStatus.REQUESTED, ScenarioCompilationStatus.WAITING_RETRY);
        return next(ScenarioCompilationStatus.RUNNING, attempt + 1, deliveryToken, null, null);
    }

    public ScenarioCompilation retry(UUID deliveryToken, String reason) {
        requireLease(deliveryToken);
        requireStatus(ScenarioCompilationStatus.RUNNING);
        return next(ScenarioCompilationStatus.WAITING_RETRY, attempt, null, null,
                Objects.requireNonNull(reason, "retry reason must not be null"));
    }

    public ScenarioCompilation publish(UUID deliveryToken, UUID packageId) {
        requireLease(deliveryToken);
        requireStatus(ScenarioCompilationStatus.RUNNING);
        return next(ScenarioCompilationStatus.PUBLISHED, attempt, null,
                Objects.requireNonNull(packageId, "package id must not be null"), null);
    }

    public ScenarioCompilation fail(UUID deliveryToken, String reason) {
        requireLease(deliveryToken);
        requireStatus(ScenarioCompilationStatus.RUNNING);
        return next(ScenarioCompilationStatus.FAILED, attempt, null, null,
                Objects.requireNonNull(reason, "failure reason must not be null"));
    }

    private ScenarioCompilation next(ScenarioCompilationStatus nextStatus, int nextAttempt,
            UUID nextLease, UUID nextPackage, String nextFailure) {
        return new ScenarioCompilation(id, bundleId, bundleRevision, inputFingerprint,
                nextStatus, nextAttempt, nextLease, nextPackage, nextFailure);
    }

    private void requireLease(UUID deliveryToken) {
        if (!Objects.equals(leaseToken, deliveryToken)) throw new IllegalStateException("compilation lease mismatch");
    }

    private void requireStatus(ScenarioCompilationStatus... allowed) {
        for (ScenarioCompilationStatus candidate : allowed) if (status == candidate) return;
        throw new IllegalStateException("compilation cannot transition from " + status);
    }

    public UUID id() { return id; }
    public ScenarioBundleId bundleId() { return bundleId; }
    public long bundleRevision() { return bundleRevision; }
    public String inputFingerprint() { return inputFingerprint; }
    public ScenarioCompilationStatus status() { return status; }
    public int attempt() { return attempt; }
    public UUID leaseToken() { return leaseToken; }
    public UUID packageId() { return packageId; }
    public String failureReason() { return failureReason; }
}
