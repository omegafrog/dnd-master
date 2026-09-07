package com.dndmaster.adventure.application.scenario.preparation;

import com.dndmaster.adventure.domain.scenario.DetailedStage;
import com.dndmaster.adventure.domain.scenario.ScenarioSourceReference;
import com.dndmaster.adventure.domain.scenario.StageArtifactRepository;
import com.dndmaster.adventure.domain.scenario.StageBackbone;
import com.dndmaster.adventure.domain.scenario.StageBackboneEntry;
import com.dndmaster.adventure.domain.scenario.SituationDefinition;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Prepares the shallow backbone and only the first playable stage before runtime start. */
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

    public Result prepare(UUID scenarioPackageId) {
        Objects.requireNonNull(scenarioPackageId, "scenario package id must not be null");
        List<ScenarioSourceReference> evidence = List.copyOf(evidenceLookup.lookup(scenarioPackageId));
        boolean scenarioBacked = evidenceLookup.isScenarioBacked(scenarioPackageId);
        if (scenarioBacked && evidence.isEmpty()) throw new IllegalStateException("published Storybook evidence is required");
        StageBackbone generated = backboneGeneration.generate(new StageBackboneGenerationPort.Request(scenarioPackageId, evidence));
        requirePackage(generated.scenarioPackageId(), scenarioPackageId);
        requireGrounding(generated.sourceRefs(), evidence, scenarioBacked, "backbone");
        generated.stages().forEach(stage -> requireGrounding(stage.sourceRefs(), evidence, scenarioBacked, "stage " + stage.stageId()));
        StageBackbone persistedBackbone = artifacts.findBackbone(scenarioPackageId, generated.revision()).orElse(null);
        boolean persistedBackboneMatchesEvidence = persistedBackbone != null
                && grounded(persistedBackbone.sourceRefs(), evidence, scenarioBacked)
                && persistedBackbone.stages().stream().allMatch(stage -> grounded(stage.sourceRefs(), evidence, scenarioBacked));
        StageBackbone backbone = persistedBackboneMatchesEvidence ? persistedBackbone : generated;
        var first = backbone.stages().getFirst();
        DetailedStage generatedStage = detailedGeneration.generate(new StageDetailedGenerationPort.Request(
                scenarioPackageId, first.stageId(), backbone.revision(), evidence));
        requirePackage(generatedStage.scenarioPackageId(), scenarioPackageId);
        if (!generatedStage.stageId().equals(first.stageId()) || generatedStage.backboneRevision() != backbone.revision()) {
            throw new IllegalStateException("detailed stage does not match first backbone stage");
        }
        DetailedStage persistedStage = persistedBackboneMatchesEvidence
                ? artifacts.findDetailedStage(scenarioPackageId, first.stageId(), backbone.revision(), generatedStage.revision()).orElse(null)
                : null;
        boolean persistedStageMatchesEvidence = persistedStage != null && grounded(persistedStage, evidence, scenarioBacked);
        DetailedStage effectiveStage = persistedStageMatchesEvidence ? persistedStage : generatedStage;
        requireGrounding(effectiveStage.sourceRefs(), evidence, scenarioBacked, "detailed stage");
        effectiveStage.revelations().forEach(revelation -> requireGrounding(revelation.sourceRefs(), evidence, scenarioBacked, "revelation " + revelation.revelationId()));
        requireGrounding(effectiveStage.threat().sourceRefs(), evidence, scenarioBacked, "threat");
        requireGrounding(effectiveStage.pressure().sourceRefs(), evidence, scenarioBacked, "pressure");
        requireGrounding(effectiveStage.funnel().sourceRefs(), evidence, scenarioBacked, "funnel");
        SituationDefinition opening = effectiveStage.situations().stream().filter(SituationDefinition::opening).findFirst()
                .orElseThrow(() -> new IllegalStateException("opening situation is required"));
        requireGrounding(opening.sourceRefs(), evidence, scenarioBacked, "opening situation");
        if (persistedStage == null && (persistedBackbone == null || persistedBackboneMatchesEvidence)) {
            if (artifacts.findBackbone(scenarioPackageId, backbone.revision()).isEmpty()) {
                artifacts.saveInitialArtifacts(backbone, generatedStage);
            } else {
                artifacts.saveDetailedStage(generatedStage, generatedStage.revision() - 1);
            }
        }
        return new Result(backbone, effectiveStage, opening);
    }

    /** Materializes and validates only the immediate next stage; no runtime state is changed here. */
    public DetailedStage prepareNext(UUID scenarioPackageId, StageBackbone backbone, String currentStageId) {
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
                scenarioPackageId, next.stageId(), backbone.revision(), evidence));
        requirePackage(generated.scenarioPackageId(), scenarioPackageId);
        if (!generated.stageId().equals(next.stageId()) || generated.backboneRevision() != backbone.revision()) {
            throw new IllegalStateException("detailed stage does not match next backbone stage");
        }
        DetailedStage current = artifacts.findDetailedStage(scenarioPackageId, next.stageId(), backbone.revision(), generated.revision()).orElse(null);
        DetailedStage effective = current == null ? generated : current;
        validateDetailedStage(effective, evidence, scenarioBacked);
        if (current == null) artifacts.saveDetailedStage(generated, generated.revision() - 1);
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
        return !evidence.isEmpty() && !references.isEmpty() && evidence.containsAll(references);
    }

    private static boolean grounded(DetailedStage stage, List<ScenarioSourceReference> evidence, boolean scenarioBacked) {
        return grounded(stage.sourceRefs(), evidence, scenarioBacked)
                && stage.revelations().stream().allMatch(revelation -> grounded(revelation.sourceRefs(), evidence, scenarioBacked))
                && grounded(stage.threat().sourceRefs(), evidence, scenarioBacked)
                && grounded(stage.pressure().sourceRefs(), evidence, scenarioBacked)
                && grounded(stage.funnel().sourceRefs(), evidence, scenarioBacked)
                && stage.situations().stream().allMatch(situation -> grounded(situation.sourceRefs(), evidence, scenarioBacked));
    }

    private static void validateDetailedStage(DetailedStage stage, List<ScenarioSourceReference> evidence,
            boolean scenarioBacked) {
        requireGrounding(stage.sourceRefs(), evidence, scenarioBacked, "detailed stage");
        stage.revelations().forEach(revelation -> requireGrounding(revelation.sourceRefs(), evidence, scenarioBacked,
                "revelation " + revelation.revelationId()));
        requireGrounding(stage.threat().sourceRefs(), evidence, scenarioBacked, "threat");
        requireGrounding(stage.pressure().sourceRefs(), evidence, scenarioBacked, "pressure");
        requireGrounding(stage.funnel().sourceRefs(), evidence, scenarioBacked, "funnel");
        stage.situations().forEach(situation -> requireGrounding(situation.sourceRefs(), evidence, scenarioBacked,
                "situation " + situation.situationId()));
    }

    public record Result(StageBackbone backbone, DetailedStage currentStage, SituationDefinition openingSituation) {}
}
