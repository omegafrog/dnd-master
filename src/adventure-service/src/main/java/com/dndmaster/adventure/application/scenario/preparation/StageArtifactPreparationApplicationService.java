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
public final class StageArtifactPreparationApplicationService {
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
        if (evidence.isEmpty()) throw new IllegalStateException("published Storybook evidence is required");

        StageBackbone generated = backboneGeneration.generate(new StageBackboneGenerationPort.Request(scenarioPackageId, evidence));
        requirePackage(generated.scenarioPackageId(), scenarioPackageId);
        StageBackbone backbone = artifacts.findBackbone(scenarioPackageId, generated.revision()).orElse(null);
        if (backbone == null) {
            artifacts.saveBackbone(generated, generated.revision() - 1);
            backbone = generated;
        }
        var first = backbone.stages().getFirst();
        DetailedStage generatedStage = detailedGeneration.generate(new StageDetailedGenerationPort.Request(
                first.stageId(), backbone.revision(), evidence));
        requirePackage(generatedStage.scenarioPackageId(), scenarioPackageId);
        if (!generatedStage.stageId().equals(first.stageId()) || generatedStage.backboneRevision() != backbone.revision()) {
            throw new IllegalStateException("detailed stage does not match first backbone stage");
        }
        SituationDefinition opening = generatedStage.situations().stream().filter(SituationDefinition::opening).findFirst()
                .orElseThrow(() -> new IllegalStateException("opening situation is required"));
        if (opening.sourceRefs().isEmpty()) throw new IllegalStateException("opening situation grounding is required");
        DetailedStage current = artifacts.findDetailedStage(scenarioPackageId, first.stageId(), backbone.revision(), generatedStage.revision()).orElse(null);
        if (current == null) artifacts.saveDetailedStage(generatedStage, generatedStage.revision() - 1);
        current = current == null ? generatedStage : current;
        return new Result(backbone, current, opening);
    }

    private static void requirePackage(UUID actual, UUID expected) {
        if (!expected.equals(actual)) throw new IllegalStateException("stage artifact package does not match request");
    }

    public record Result(StageBackbone backbone, DetailedStage currentStage, SituationDefinition openingSituation) {}
}
