package com.dndmaster.adventure.domain.scenario;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class ScenarioPackage {
    private final UUID packageId;
    private final ScenarioBundleId bundleId;
    private final long bundleRevision;
    private final String inputFingerprint;
    private final List<ScenarioBundleDocumentSelection> documents;
    private final List<ScenarioResolutionUnit> units;
    private final ScenarioCompilationReport report;
    private final CharacterLimit characterLimit;

    private ScenarioPackage(
            UUID packageId,
            ScenarioBundleId bundleId,
            long bundleRevision,
            String inputFingerprint,
            List<ScenarioBundleDocumentSelection> documents,
            List<ScenarioResolutionUnit> units,
            ScenarioCompilationReport report,
            CharacterLimit characterLimit) {
        this.packageId = Objects.requireNonNull(packageId, "package id must not be null");
        this.bundleId = Objects.requireNonNull(bundleId, "bundle id must not be null");
        this.inputFingerprint = Objects.requireNonNull(inputFingerprint, "input fingerprint must not be null");
        if (bundleRevision <= 0 || inputFingerprint.isBlank()) {
            throw new IllegalArgumentException("package version must have a positive revision and fingerprint");
        }
        this.bundleRevision = bundleRevision;
        this.documents = List.copyOf(Objects.requireNonNull(documents, "documents must not be null"));
        this.units = List.copyOf(Objects.requireNonNull(units, "units must not be null"));
        this.report = Objects.requireNonNull(report, "report must not be null");
        this.characterLimit = Objects.requireNonNull(characterLimit, "character limit must not be null");
    }

    public static ScenarioPackage publish(
            ScenarioBundleId bundleId,
            long bundleRevision,
            String inputFingerprint,
            List<ScenarioBundleDocumentSelection> documents,
            List<ScenarioResolutionUnit> units,
            ScenarioCompilationReport report) {
        return publish(bundleId, bundleRevision, inputFingerprint, documents, units, report, CharacterLimit.defaultLimit());
    }

    public static ScenarioPackage publish(
            ScenarioBundleId bundleId,
            long bundleRevision,
            String inputFingerprint,
            List<ScenarioBundleDocumentSelection> documents,
            List<ScenarioResolutionUnit> units,
            ScenarioCompilationReport report,
            CharacterLimit characterLimit) {
        return new ScenarioPackage(UUID.randomUUID(), bundleId, bundleRevision, inputFingerprint, documents, units, report, characterLimit);
    }

    public static ScenarioPackage rehydrate(
            UUID packageId, ScenarioBundleId bundleId, long bundleRevision, String inputFingerprint,
            List<ScenarioBundleDocumentSelection> documents, List<ScenarioResolutionUnit> units,
            ScenarioCompilationReport report) {
        return rehydrate(packageId, bundleId, bundleRevision, inputFingerprint, documents, units, report, CharacterLimit.defaultLimit());
    }

    public static ScenarioPackage rehydrate(
            UUID packageId, ScenarioBundleId bundleId, long bundleRevision, String inputFingerprint,
            List<ScenarioBundleDocumentSelection> documents, List<ScenarioResolutionUnit> units,
            ScenarioCompilationReport report, CharacterLimit characterLimit) {
        return new ScenarioPackage(packageId, bundleId, bundleRevision, inputFingerprint, documents, units, report, characterLimit);
    }

    public List<ScenarioResolutionUnit> runtimeCandidates() {
        return units.stream().filter(unit -> unit.status() != ResolutionStatus.INVALID).toList();
    }

    public UUID packageId() { return packageId; }
    public ScenarioBundleId bundleId() { return bundleId; }
    public long bundleRevision() { return bundleRevision; }
    public String inputFingerprint() { return inputFingerprint; }
    public List<ScenarioBundleDocumentSelection> documents() { return documents; }
    public List<ScenarioResolutionUnit> units() { return units; }
    public ScenarioCompilationReport report() { return report; }
    public CharacterLimit characterLimit() { return characterLimit; }
}
