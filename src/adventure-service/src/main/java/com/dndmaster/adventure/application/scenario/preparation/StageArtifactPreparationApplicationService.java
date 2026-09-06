package com.dndmaster.adventure.application.scenario.preparation;

import com.dndmaster.adventure.domain.scenario.DetailedStage;
import com.dndmaster.adventure.domain.scenario.ScenarioSourceReference;
import com.dndmaster.adventure.domain.scenario.StageArtifactRepository;
import com.dndmaster.adventure.domain.scenario.StageBackbone;
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
        StageBackbone backbone = artifacts.findBackbone(scenarioPackageId, generated.revision()).orElse(null);
        if (backbone == null) {
            backbone = generated;
        }
        var first = backbone.stages().getFirst();
        DetailedStage generatedStage = detailedGeneration.generate(new StageDetailedGenerationPort.Request(
                scenarioPackageId, first.stageId(), backbone.revision(), evidence));
        requirePackage(generatedStage.scenarioPackageId(), scenarioPackageId);
        if (!generatedStage.stageId().equals(first.stageId()) || generatedStage.backboneRevision() != backbone.revision()) {
            throw new IllegalStateException("detailed stage does not match first backbone stage");
        }
        DetailedStage current = artifacts.findDetailedStage(scenarioPackageId, first.stageId(), backbone.revision(), generatedStage.revision()).orElse(null);
        DetailedStage effectiveStage = current == null ? generatedStage : current;
        requireGrounding(effectiveStage.sourceRefs(), evidence, scenarioBacked, "detailed stage");
        effectiveStage.revelations().forEach(revelation -> requireGrounding(revelation.sourceRefs(), evidence, scenarioBacked, "revelation " + revelation.revelationId()));
        requireGrounding(effectiveStage.threat().sourceRefs(), evidence, scenarioBacked, "threat");
        requireGrounding(effectiveStage.pressure().sourceRefs(), evidence, scenarioBacked, "pressure");
        requireGrounding(effectiveStage.funnel().sourceRefs(), evidence, scenarioBacked, "funnel");
        SituationDefinition opening = effectiveStage.situations().stream().filter(SituationDefinition::opening).findFirst()
                .orElseThrow(() -> new IllegalStateException("opening situation is required"));
        requireGrounding(opening.sourceRefs(), evidence, scenarioBacked, "opening situation");
        if (current == null) {
            if (artifacts.findBackbone(scenarioPackageId, backbone.revision()).isEmpty()) {
                artifacts.saveInitialArtifacts(backbone, generatedStage);
            } else {
                artifacts.saveDetailedStage(generatedStage, generatedStage.revision() - 1);
            }
        }
        current = effectiveStage;
        return new Result(backbone, current, opening);
    }

    private static void requirePackage(UUID actual, UUID expected) {
        if (!expected.equals(actual)) throw new IllegalStateException("stage artifact package does not match request");
    }

    private static void requireGrounding(List<ScenarioSourceReference> references, List<ScenarioSourceReference> evidence,
            boolean scenarioBacked, String subject) {
        if (!scenarioBacked) {
            if (!references.isEmpty()) throw new IllegalStateException(subject + " contains scenario grounding in Rulebook-Only Bundle");
            return;
        }
        if (evidence.isEmpty() || references.isEmpty() || !evidence.containsAll(references)) {
            throw new IllegalStateException(subject + " contains invalid grounding");
        }
    }

    public record Result(StageBackbone backbone, DetailedStage currentStage, SituationDefinition openingSituation) {}
}
