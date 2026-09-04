package com.dndmaster.adventure.domain.scenario;

import java.util.Objects;
import java.util.List;
import java.util.UUID;

public final class ScenarioCompilation {
    private final UUID id;
    private final ScenarioBundleId bundleId;
    private final long bundleRevision;
    private final String inputFingerprint;
    private final String idempotencyKey;
    private final ScenarioCompilationStatus status;
    private final int attempt;
    private final UUID leaseToken;
    private final UUID packageId;
    private final String failureReason;
    private final ScenarioCompilationInputSnapshot inputSnapshot;
    private final List<ScenarioCompilationDiagnostic> diagnostics;

    private ScenarioCompilation(
            UUID id, ScenarioBundleId bundleId, long bundleRevision, String inputFingerprint, String idempotencyKey,
            ScenarioCompilationStatus status, int attempt, UUID leaseToken, UUID packageId, String failureReason,
            ScenarioCompilationInputSnapshot inputSnapshot, List<ScenarioCompilationDiagnostic> diagnostics) {
        this.id = Objects.requireNonNull(id, "compilation id must not be null");
        this.bundleId = Objects.requireNonNull(bundleId, "bundle id must not be null");
        this.inputFingerprint = Objects.requireNonNull(inputFingerprint, "input fingerprint must not be null");
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey, "idempotency key must not be null");
        if (bundleRevision <= 0 || inputFingerprint.isBlank() || idempotencyKey.isBlank() || attempt < 0) {
            throw new IllegalArgumentException("invalid compilation identity or attempt");
        }
        this.bundleRevision = bundleRevision;
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.attempt = attempt;
        this.leaseToken = leaseToken;
        this.packageId = packageId;
        this.failureReason = failureReason;
        this.inputSnapshot = inputSnapshot;
        this.diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics must not be null"));
    }

    public static ScenarioCompilation request(ScenarioBundleId bundleId, long bundleRevision, String inputFingerprint) {
        return request(bundleId, bundleRevision, inputFingerprint, inputFingerprint);
    }
    public static ScenarioCompilation request(ScenarioBundleId bundleId, long bundleRevision, String inputFingerprint, String idempotencyKey) {
        return new ScenarioCompilation(UUID.randomUUID(), bundleId, bundleRevision, inputFingerprint,
                idempotencyKey,
                ScenarioCompilationStatus.REQUESTED, 0, null, null, null, null, List.of());
    }

    public static ScenarioCompilation request(
            ScenarioCompilationInputSnapshot input, String inputFingerprint, String idempotencyKey) {
        Objects.requireNonNull(input, "input snapshot must not be null");
        return new ScenarioCompilation(UUID.randomUUID(), input.bundleId(), input.bundleRevision(), inputFingerprint,
                idempotencyKey, ScenarioCompilationStatus.QUEUED, 0, null, null, null, input, List.of());
    }

    public static ScenarioCompilation rehydrate(
            UUID id, ScenarioBundleId bundleId, long bundleRevision, String inputFingerprint, String idempotencyKey,
            ScenarioCompilationStatus status, int attempt, UUID leaseToken, UUID packageId, String failureReason) {
        return new ScenarioCompilation(id, bundleId, bundleRevision, inputFingerprint, idempotencyKey, status, attempt,
                leaseToken, packageId, failureReason, null, List.of());
    }

    public static ScenarioCompilation rehydrate(
            UUID id, ScenarioBundleId bundleId, long bundleRevision, String inputFingerprint, String idempotencyKey,
            ScenarioCompilationStatus status, int attempt, UUID leaseToken, UUID packageId, String failureReason,
            ScenarioCompilationInputSnapshot inputSnapshot, List<ScenarioCompilationDiagnostic> diagnostics) {
        return new ScenarioCompilation(id, bundleId, bundleRevision, inputFingerprint, idempotencyKey, status, attempt,
                leaseToken, packageId, failureReason, inputSnapshot, diagnostics == null ? List.of() : diagnostics);
    }

    public static ScenarioCompilation rehydrate(
            UUID id, ScenarioBundleId bundleId, long bundleRevision, String inputFingerprint,
            ScenarioCompilationStatus status, int attempt, UUID leaseToken, UUID packageId, String failureReason) {
        return rehydrate(id, bundleId, bundleRevision, inputFingerprint, inputFingerprint, status, attempt, leaseToken, packageId, failureReason);
    }

    public ScenarioCompilation claim(UUID deliveryToken) {
        Objects.requireNonNull(deliveryToken, "delivery token must not be null");
        if ((status == ScenarioCompilationStatus.RUNNING || status == ScenarioCompilationStatus.PROCESSING)
                && Objects.equals(leaseToken, deliveryToken)) {
            throw new IllegalStateException("compilation lease is still active");
        }
        requireStatus(ScenarioCompilationStatus.QUEUED, ScenarioCompilationStatus.REQUESTED,
                ScenarioCompilationStatus.WAITING_RETRY, ScenarioCompilationStatus.RUNNING,
                ScenarioCompilationStatus.PROCESSING);
        ScenarioCompilationStatus claimedStatus = status == ScenarioCompilationStatus.QUEUED
                || status == ScenarioCompilationStatus.PROCESSING
                ? ScenarioCompilationStatus.PROCESSING : ScenarioCompilationStatus.RUNNING;
        return next(claimedStatus, attempt + 1, deliveryToken, null, null);
    }

    public ScenarioCompilation retry(UUID deliveryToken, String reason) {
        requireLease(deliveryToken);
        requireStatus(ScenarioCompilationStatus.RUNNING, ScenarioCompilationStatus.PROCESSING);
        return next(ScenarioCompilationStatus.WAITING_RETRY, attempt, null, null,
                Objects.requireNonNull(reason, "retry reason must not be null"));
    }

    public ScenarioCompilation publish(UUID deliveryToken, UUID packageId) {
        requireLease(deliveryToken);
        requireStatus(ScenarioCompilationStatus.RUNNING, ScenarioCompilationStatus.PROCESSING);
        return next(ScenarioCompilationStatus.PUBLISHED, attempt, null,
                Objects.requireNonNull(packageId, "package id must not be null"), null);
    }

    public ScenarioCompilation complete(UUID deliveryToken, UUID packageId, List<ScenarioCompilationDiagnostic> diagnostics) {
        requireLease(deliveryToken);
        requireStatus(ScenarioCompilationStatus.PROCESSING, ScenarioCompilationStatus.RUNNING);
        return nextWithDiagnostics(ScenarioCompilationStatus.COMPLETED, attempt, null,
                Objects.requireNonNull(packageId, "package id must not be null"), null, diagnostics);
    }

    public ScenarioCompilation block(UUID deliveryToken, List<ScenarioCompilationDiagnostic> diagnostics) {
        requireLease(deliveryToken);
        requireStatus(ScenarioCompilationStatus.PROCESSING, ScenarioCompilationStatus.RUNNING);
        return nextWithDiagnostics(ScenarioCompilationStatus.BLOCKED, attempt, null, null, null, diagnostics);
    }

    public ScenarioCompilation fail(UUID deliveryToken, String reason) {
        requireLease(deliveryToken);
        requireStatus(ScenarioCompilationStatus.RUNNING, ScenarioCompilationStatus.PROCESSING);
        return next(ScenarioCompilationStatus.FAILED, attempt, null, null,
                Objects.requireNonNull(reason, "failure reason must not be null"));
    }

    private ScenarioCompilation next(ScenarioCompilationStatus nextStatus, int nextAttempt,
            UUID nextLease, UUID nextPackage, String nextFailure) {
        return new ScenarioCompilation(id, bundleId, bundleRevision, inputFingerprint, idempotencyKey,
                nextStatus, nextAttempt, nextLease, nextPackage, nextFailure, inputSnapshot, diagnostics);
    }

    private ScenarioCompilation nextWithDiagnostics(ScenarioCompilationStatus nextStatus, int nextAttempt,
            UUID nextLease, UUID nextPackage, String nextFailure, List<ScenarioCompilationDiagnostic> nextDiagnostics) {
        return new ScenarioCompilation(id, bundleId, bundleRevision, inputFingerprint, idempotencyKey,
                nextStatus, nextAttempt, nextLease, nextPackage, nextFailure, inputSnapshot,
                nextDiagnostics == null ? List.of() : nextDiagnostics);
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
    public String idempotencyKey() { return idempotencyKey; }
    public ScenarioCompilationStatus status() { return status; }
    public int attempt() { return attempt; }
    public UUID leaseToken() { return leaseToken; }
    public UUID packageId() { return packageId; }
    public String failureReason() { return failureReason; }
    public ScenarioCompilationInputSnapshot inputSnapshot() { return inputSnapshot; }
    public List<ScenarioCompilationDiagnostic> diagnostics() { return diagnostics; }
}
