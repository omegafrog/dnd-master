package com.dndmaster.adventure.application.scenario.preparation;

import com.dndmaster.adventure.domain.scenario.DetailedStage;
import com.dndmaster.adventure.domain.scenario.ScenarioSourceReference;
import com.dndmaster.adventure.domain.scenario.StageArtifactRepository;
import com.dndmaster.adventure.domain.scenario.StageBackbone;
import com.dndmaster.adventure.domain.scenario.StageBackboneEntry;
import com.dndmaster.adventure.domain.scenario.SituationDefinition;
import com.dndmaster.adventure.domain.scenario.SituationProvenance;
import com.dndmaster.adventure.domain.scenario.RequiredSituation;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.SessionId;

/** Prepares and publishes the complete initial set before runtime start. */
public final class StageArtifactPreparationApplicationService implements StageArtifactPreparationPort {
    private final StageArtifactRepository artifacts;
    private final StorybookEvidenceLookupPort evidenceLookup;
    private final StageBackboneGenerationPort backboneGeneration;
    private final StageDetailedGenerationPort detailedGeneration;

    public StageArtifactPreparationApplicationService(StageArtifactRepository artifacts,
            StorybookEvidenceLookupPort evidenceLookup, StageBackboneGenerationPort backboneGeneration,
            StageDetailedGenerationPort detailedGeneration) {
        this.artifacts = Objects.requireNonNull(artifacts);
        this.evidenceLookup = Objects.requireNonNull(evidenceLookup);
        this.backboneGeneration = Objects.requireNonNull(backboneGeneration);
        this.detailedGeneration = Objects.requireNonNull(detailedGeneration);
    }

    public Result prepare(AdventureId adventureId, SessionId sessionId, UUID scenarioPackageId) {
        Objects.requireNonNull(adventureId, "adventure id must not be null");
        Objects.requireNonNull(sessionId, "session id must not be null");
        Objects.requireNonNull(scenarioPackageId, "scenario package id must not be null");
        StageBackbone persistedBackbone = artifacts.findBackbone(adventureId, scenarioPackageId, 1).orElse(null);
        if (persistedBackbone != null) {
            requirePackage(persistedBackbone.scenarioPackageId(), scenarioPackageId);
            List<DetailedStage> persistedStages = persistedBackbone.stages().stream().map(stage ->
                    artifacts.findDetailedStage(adventureId, scenarioPackageId, stage.stageId(), 1, 1)
                            .orElseThrow(() -> new IllegalStateException("initial situation set is incomplete"))).toList();
            persistedStages.forEach(stage -> {
                requirePackage(stage.scenarioPackageId(), scenarioPackageId);
                if (!persistedBackbone.stages().stream().anyMatch(entry -> entry.stageId().equals(stage.stageId()))
                        || stage.backboneRevision() != persistedBackbone.revision()) {
                    throw new IllegalStateException("saved stage does not match saved backbone");
                }
                stage.situations().forEach(situation -> {
                    if (situation.provenance() == SituationProvenance.SOURCE_EXTRACTED && situation.sourceRefs().isEmpty()
                            || situation.provenance() == SituationProvenance.GM_SUPPLEMENTED && !situation.sourceRefs().isEmpty()) {
                        throw new IllegalStateException("saved situation has invalid provenance");
                    }
                });
            });
            java.util.Set<UUID> savedMapIds = persistedStages.stream().flatMap(stage -> stage.situations().stream())
                    .map(SituationDefinition::mapDefinitionId).filter(Objects::nonNull)
                    .collect(java.util.stream.Collectors.toSet());
            validateInitialSituationSet(persistedStages, savedMapIds, persistedBackbone.requiredSituations());
            DetailedStage firstStage = persistedStages.getFirst();
            SituationDefinition opening = firstStage.situations().stream().filter(SituationDefinition::opening).findFirst()
                    .orElseThrow(() -> new IllegalStateException("opening situation is required"));
            return new Result(persistedBackbone, persistedStages, firstStage, opening);
        }
        List<ScenarioSourceReference> evidence = List.copyOf(evidenceLookup.lookup(scenarioPackageId));
        boolean scenarioBacked = evidenceLookup.isScenarioBacked(scenarioPackageId);
        java.util.Set<UUID> providedMapIds = evidenceLookup.providedMapIds(scenarioPackageId);
        if (scenarioBacked && evidence.isEmpty()) throw new IllegalStateException("published Storybook evidence is required");
        StageBackbone generated = backboneGeneration.generate(new StageBackboneGenerationPort.Request(
                adventureId, sessionId, scenarioPackageId, evidence));
        requirePackage(generated.scenarioPackageId(), scenarioPackageId);
        requireGrounding(generated.sourceRefs(), evidence, scenarioBacked, "backbone");
        generated.stages().forEach(stage -> requireGrounding(stage.sourceRefs(), evidence, scenarioBacked, "stage " + stage.stageId()));
        validateRequiredSourceRefs(generated.requiredSituations(), evidence);
        StageBackbone backbone = generated;
        var first = backbone.stages().getFirst();
        List<DetailedStage> effectiveStages = backbone.stages().stream().map(stage -> {
                DetailedStage detailed = detailedGeneration.generate(new StageDetailedGenerationPort.Request(
                        adventureId, sessionId, scenarioPackageId, stage.stageId(), backbone.revision(), evidence,
                        backbone.requiredSituations(), stage.stageId().equals(first.stageId())));
                requirePackage(detailed.scenarioPackageId(), scenarioPackageId);
                if (!detailed.stageId().equals(stage.stageId()) || detailed.backboneRevision() != backbone.revision()) {
                    throw new IllegalStateException("detailed stage does not match backbone stage");
                }
                validateDetailedStage(detailed, evidence, scenarioBacked, providedMapIds);
                return detailed;
            }).toList();
        validateInitialSituationSet(effectiveStages, providedMapIds, backbone.requiredSituations());
        DetailedStage effectiveStage = effectiveStages.stream().filter(stage -> stage.stageId().equals(first.stageId()))
                .findFirst().orElseThrow();
        SituationDefinition opening = effectiveStage.situations().stream().filter(SituationDefinition::opening).findFirst()
                .orElseThrow(() -> new IllegalStateException("opening situation is required"));
        artifacts.saveInitialArtifacts(adventureId, backbone, effectiveStages);
        return new Result(backbone, effectiveStages, effectiveStage, opening);
    }

    /** Materializes and validates only the immediate next stage; no runtime state is changed here. */
    public DetailedStage prepareNext(AdventureId adventureId, SessionId sessionId, UUID scenarioPackageId,
            StageBackbone backbone, String currentStageId) {
        Objects.requireNonNull(adventureId, "adventure id must not be null");
        Objects.requireNonNull(sessionId, "session id must not be null");
        Objects.requireNonNull(scenarioPackageId, "scenario package id must not be null");
        Objects.requireNonNull(backbone, "stage backbone must not be null");
        if (!scenarioPackageId.equals(backbone.scenarioPackageId())) throw new IllegalArgumentException("backbone package does not match request");
        if (currentStageId == null || currentStageId.isBlank()) throw new IllegalArgumentException("current stage id is required");
        int currentOrder = backbone.stages().stream().filter(stage -> stage.stageId().equals(currentStageId.trim()))
                .mapToInt(stage -> stage.order()).findFirst().orElseThrow(() -> new IllegalArgumentException("current stage is not in backbone"));
        StageBackboneEntry next = backbone.stages().stream().filter(stage -> stage.order() == currentOrder + 1).findFirst()
                .orElseThrow(() -> new IllegalStateException("current stage has no next stage"));
        List<ScenarioSourceReference> evidence = List.copyOf(evidenceLookup.lookup(scenarioPackageId));
        boolean scenarioBacked = evidenceLookup.isScenarioBacked(scenarioPackageId);
        if (scenarioBacked && evidence.isEmpty()) throw new IllegalStateException("published Storybook evidence is required");
        DetailedStage generated = detailedGeneration.generate(new StageDetailedGenerationPort.Request(
                adventureId, sessionId, scenarioPackageId, next.stageId(), backbone.revision(), evidence,
                backbone.requiredSituations()));
        requirePackage(generated.scenarioPackageId(), scenarioPackageId);
        if (!generated.stageId().equals(next.stageId()) || generated.backboneRevision() != backbone.revision()) {
            throw new IllegalStateException("detailed stage does not match next backbone stage");
        }
        DetailedStage current = artifacts.findDetailedStage(adventureId, scenarioPackageId, next.stageId(), backbone.revision(), generated.revision()).orElse(null);
        DetailedStage effective = current == null ? generated : current;
        validateDetailedStage(effective, evidence, scenarioBacked, evidenceLookup.providedMapIds(scenarioPackageId));
        if (current == null) artifacts.saveDetailedStage(adventureId, generated, generated.revision() - 1);
        return effective;
    }

    private static void requirePackage(UUID actual, UUID expected) {
        if (!expected.equals(actual)) throw new IllegalStateException("stage artifact package does not match request");
    }

    private static void requireGrounding(List<ScenarioSourceReference> references, List<ScenarioSourceReference> evidence,
            boolean scenarioBacked, String subject) {
        if (!grounded(references, evidence, scenarioBacked)) {
            if (!scenarioBacked && !references.isEmpty()) {
                throw new IllegalStateException(subject + " contains scenario grounding in Rulebook-Only Bundle");
            }
            throw new IllegalStateException(subject + " contains invalid grounding");
        }
    }

    private static boolean grounded(List<ScenarioSourceReference> references, List<ScenarioSourceReference> evidence,
            boolean scenarioBacked) {
        if (!scenarioBacked) return references.isEmpty();
        return references.isEmpty() || (!evidence.isEmpty() && evidence.containsAll(references));
    }

    private static void validateDetailedStage(DetailedStage stage, List<ScenarioSourceReference> evidence,
            boolean scenarioBacked, java.util.Set<UUID> providedMapIds) {
        requireGrounding(stage.sourceRefs(), evidence, scenarioBacked, "detailed stage");
        stage.revelations().forEach(revelation -> requireGrounding(revelation.sourceRefs(), evidence, scenarioBacked,
                "revelation " + revelation.revelationId()));
        requireGrounding(stage.threat().sourceRefs(), evidence, scenarioBacked, "threat");
        requireGrounding(stage.pressure().sourceRefs(), evidence, scenarioBacked, "pressure");
        requireGrounding(stage.funnel().sourceRefs(), evidence, scenarioBacked, "funnel");
        stage.situations().forEach(situation -> {
            if (!groundedSituation(situation, evidence, scenarioBacked)) {
                throw new IllegalStateException("situation " + situation.situationId() + " contains invalid provenance");
            }
            if (situation.mapDefinitionId() != null && !providedMapIds.contains(situation.mapDefinitionId())) {
                throw new IllegalStateException("situation references a map not provided to this adventure");
            }
        });
    }

    private static boolean groundedSituation(SituationDefinition situation, List<ScenarioSourceReference> evidence,
            boolean scenarioBacked) {
        if (situation.provenance() == SituationProvenance.SOURCE_EXTRACTED) {
            return !situation.sourceRefs().isEmpty() && grounded(situation.sourceRefs(), evidence, scenarioBacked);
        }
        return situation.sourceRefs().isEmpty();
    }

    private static void validateRequiredSourceRefs(List<RequiredSituation> required,
            List<ScenarioSourceReference> evidence) {
        for (RequiredSituation item : required) {
            if (!evidence.containsAll(item.sourceRefs())) {
                throw new IllegalStateException("reviewed required situation cites an unavailable original passage");
            }
        }
    }

    private static void validateInitialSituationSet(List<DetailedStage> stages, java.util.Set<UUID> providedMapIds,
            List<RequiredSituation> required) {
        java.util.Map<String, SituationDefinition> situations = new java.util.LinkedHashMap<>();
        for (DetailedStage stage : stages) {
            for (SituationDefinition situation : stage.situations()) {
                if (situations.putIfAbsent(situation.situationId(), situation) != null) {
                    throw new IllegalStateException("situation ids must be unique across the initial set");
                }
                if (situation.mapDefinitionId() != null && !providedMapIds.contains(situation.mapDefinitionId())) {
                    throw new IllegalStateException("situation references a map not provided to this adventure");
                }
            }
        }
        long intermediateCandidates = situations.values().stream()
                .filter(situation -> !situation.opening() && !situation.required())
                .count();
        if (situations.values().stream().noneMatch(SituationDefinition::required)) {
            throw new IllegalStateException("initial situation set must include a required situation");
        }
        if (intermediateCandidates < 2) {
            throw new IllegalStateException("initial situation set must include multiple intermediate candidates");
        }
        for (RequiredSituation item : required) {
            SituationDefinition actual = situations.get(item.situationId());
            if (actual == null || !actual.required() || actual.provenance() != SituationProvenance.SOURCE_EXTRACTED
                    || !java.util.Set.copyOf(actual.sourceRefs()).equals(java.util.Set.copyOf(item.sourceRefs()))
                    || !actual.prerequisiteSituationIds().equals(item.prerequisiteSituationIds())) {
                throw new IllegalStateException("initial situation set does not preserve a reviewed required situation");
            }
        }
        for (SituationDefinition actual : situations.values()) {
            if (actual.required() && actual.provenance() == SituationProvenance.SOURCE_EXTRACTED
                    && required.stream().noneMatch(item -> item.situationId().equals(actual.situationId()))
                    && !required.isEmpty()) {
                throw new IllegalStateException("initial situation set has an unreviewed required situation");
            }
        }
        for (SituationDefinition situation : situations.values()) {
            if (!situations.keySet().containsAll(situation.prerequisiteSituationIds())) {
                throw new IllegalStateException("situation prerequisite does not exist in the initial set");
            }
        }
        java.util.Set<String> visiting = new java.util.HashSet<>();
        java.util.Set<String> visited = new java.util.HashSet<>();
        for (String id : situations.keySet()) visit(id, situations, visiting, visited);
    }

    private static void visit(String id, java.util.Map<String, SituationDefinition> situations,
            java.util.Set<String> visiting, java.util.Set<String> visited) {
        if (visited.contains(id)) return;
        if (!visiting.add(id)) throw new IllegalStateException("situation prerequisites cannot contain a cycle");
        situations.get(id).prerequisiteSituationIds().forEach(prerequisite -> visit(prerequisite, situations, visiting, visited));
        visiting.remove(id);
        visited.add(id);
    }

    public record Result(StageBackbone backbone, List<DetailedStage> detailedStages, DetailedStage currentStage,
            SituationDefinition openingSituation) {
        public Result { detailedStages = List.copyOf(detailedStages); }
    }
}
