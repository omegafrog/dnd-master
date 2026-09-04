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
    private final CharacterCreationBlueprint characterCreationBlueprint;
    private final List<MapDefinition> mapDefinitions;
    private final List<StoryMapBinding> storyMapBindings;
    private final ScenarioModel scenarioModel;

    private ScenarioPackage(
            UUID packageId,
            ScenarioBundleId bundleId,
            long bundleRevision,
            String inputFingerprint,
            List<ScenarioBundleDocumentSelection> documents,
            List<ScenarioResolutionUnit> units,
            ScenarioCompilationReport report,
            CharacterLimit characterLimit,
            CharacterCreationBlueprint characterCreationBlueprint,
            List<MapDefinition> mapDefinitions, List<StoryMapBinding> storyMapBindings,
            ScenarioModel scenarioModel) {
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
        this.characterCreationBlueprint = characterCreationBlueprint;
        this.mapDefinitions = List.copyOf(Objects.requireNonNull(mapDefinitions, "map definitions must not be null"));
        this.storyMapBindings = List.copyOf(Objects.requireNonNull(storyMapBindings, "story map bindings must not be null"));
        this.scenarioModel = scenarioModel;
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
        return new ScenarioPackage(UUID.randomUUID(), bundleId, bundleRevision, inputFingerprint, documents, units, report, characterLimit, null, List.of(), List.of(), null);
    }

    public static ScenarioPackage publish(ScenarioBundleId bundleId, long bundleRevision, String inputFingerprint,
            List<ScenarioBundleDocumentSelection> documents, List<ScenarioResolutionUnit> units,
            ScenarioCompilationReport report, CharacterLimit characterLimit,
            CharacterCreationBlueprint blueprint) {
        return new ScenarioPackage(UUID.randomUUID(), bundleId, bundleRevision, inputFingerprint, documents, units,
                report, characterLimit, Objects.requireNonNull(blueprint, "blueprint must not be null"), List.of(), List.of(), null);
    }

    public static ScenarioPackage publishWithMaps(ScenarioBundleId bundleId, long bundleRevision, String inputFingerprint,
            List<ScenarioBundleDocumentSelection> documents, List<ScenarioResolutionUnit> units,
            ScenarioCompilationReport report, CharacterLimit characterLimit, CharacterCreationBlueprint blueprint,
            List<MapDefinition> mapDefinitions, List<StoryMapBinding> storyMapBindings) {
        return new ScenarioPackage(UUID.randomUUID(), bundleId, bundleRevision, inputFingerprint, documents, units,
                report, characterLimit, blueprint, mapDefinitions, storyMapBindings, null);
    }

    /** Publishes the lockable ScenarioModel in the same package/version boundary. */
    public static ScenarioPackage publishWithScenarioModel(
            ScenarioBundleId bundleId, long bundleRevision, String inputFingerprint,
            List<ScenarioBundleDocumentSelection> documents, List<ScenarioResolutionUnit> units,
            ScenarioCompilationReport report, CharacterLimit characterLimit, CharacterCreationBlueprint blueprint,
            List<MapDefinition> mapDefinitions, List<StoryMapBinding> storyMapBindings, ScenarioModel scenarioModel) {
        return new ScenarioPackage(UUID.randomUUID(), bundleId, bundleRevision, inputFingerprint, documents, units,
                report, characterLimit, blueprint, mapDefinitions, storyMapBindings,
                Objects.requireNonNull(scenarioModel, "scenario model must not be null"));
    }

    public static ScenarioPackage rehydrate(
            UUID packageId, ScenarioBundleId bundleId, long bundleRevision, String inputFingerprint,
            List<ScenarioBundleDocumentSelection> documents, List<ScenarioResolutionUnit> units,
            ScenarioCompilationReport report) {
        return rehydrate(packageId, bundleId, bundleRevision, inputFingerprint, documents, units, report, CharacterLimit.defaultLimit(), null);
    }

    public static ScenarioPackage rehydrate(
            UUID packageId, ScenarioBundleId bundleId, long bundleRevision, String inputFingerprint,
            List<ScenarioBundleDocumentSelection> documents, List<ScenarioResolutionUnit> units,
            ScenarioCompilationReport report, CharacterLimit characterLimit) {
        return new ScenarioPackage(packageId, bundleId, bundleRevision, inputFingerprint, documents, units, report, characterLimit, null, List.of(), List.of(), null);
    }

    public static ScenarioPackage rehydrate(UUID packageId, ScenarioBundleId bundleId, long bundleRevision,
            String inputFingerprint, List<ScenarioBundleDocumentSelection> documents, List<ScenarioResolutionUnit> units,
            ScenarioCompilationReport report, CharacterLimit characterLimit, CharacterCreationBlueprint blueprint) {
        return new ScenarioPackage(packageId, bundleId, bundleRevision, inputFingerprint, documents, units, report,
                characterLimit, blueprint, List.of(), List.of(), null);
    }

    public static ScenarioPackage rehydrateWithMaps(UUID packageId, ScenarioBundleId bundleId, long bundleRevision,
            String inputFingerprint, List<ScenarioBundleDocumentSelection> documents, List<ScenarioResolutionUnit> units,
            ScenarioCompilationReport report, CharacterLimit characterLimit, CharacterCreationBlueprint blueprint,
            List<MapDefinition> mapDefinitions, List<StoryMapBinding> storyMapBindings) {
        return new ScenarioPackage(packageId, bundleId, bundleRevision, inputFingerprint, documents, units, report,
                characterLimit, blueprint, mapDefinitions, storyMapBindings, null);
    }

    public static ScenarioPackage rehydrateWithScenarioModel(UUID packageId, ScenarioBundleId bundleId, long bundleRevision,
            String inputFingerprint, List<ScenarioBundleDocumentSelection> documents, List<ScenarioResolutionUnit> units,
            ScenarioCompilationReport report, CharacterLimit characterLimit, CharacterCreationBlueprint blueprint,
            List<MapDefinition> mapDefinitions, List<StoryMapBinding> storyMapBindings, ScenarioModel scenarioModel) {
        return new ScenarioPackage(packageId, bundleId, bundleRevision, inputFingerprint, documents, units, report,
                characterLimit, blueprint, mapDefinitions, storyMapBindings, scenarioModel);
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
    public CharacterCreationBlueprint characterCreationBlueprint() { return characterCreationBlueprint; }
    public List<MapDefinition> mapDefinitions() { return mapDefinitions; }
    public List<StoryMapBinding> storyMapBindings() { return storyMapBindings; }
    public ScenarioModel scenarioModel() { return scenarioModel; }

    public ScenarioPackage withScenarioModel(ScenarioModel model) {
        return new ScenarioPackage(packageId, bundleId, bundleRevision, inputFingerprint, documents, units, report,
                characterLimit, characterCreationBlueprint, mapDefinitions, storyMapBindings,
                Objects.requireNonNull(model, "scenario model must not be null"));
    }

    public boolean isReady() {
        return scenarioModel != null && scenarioModel.hasCoreResolutionInformation()
                && report.outcome() != CompilationOutcome.FAILED;
    }
}
