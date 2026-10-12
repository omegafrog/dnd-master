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
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(StageArtifactPreparationApplicationService.class);
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
            validateInitialSituationSet(persistedStages, savedMapIds, persistedBackbone.stages(),
                    persistedBackbone.requiredSituations());
            DetailedStage firstStage = persistedStages.getFirst();
            SituationDefinition opening = firstStage.situations().stream().filter(SituationDefinition::opening).findFirst()
                    .orElseThrow(() -> new IllegalStateException("opening situation is required"));
            return new Result(persistedBackbone, persistedStages, firstStage, opening);
        }
        StorybookEvidenceLookupPort.PreparationEvidence preparationEvidence = evidenceLookup.lookupForPreparation(scenarioPackageId, sessionId);
        List<ScenarioSourceReference> evidence = preparationEvidence.sourceRefs();
        boolean scenarioBacked = evidenceLookup.isScenarioBacked(scenarioPackageId);
        java.util.Set<UUID> providedMapIds = evidenceLookup.providedMapIds(scenarioPackageId);
        if (scenarioBacked && evidence.isEmpty()) throw new IllegalStateException("published Storybook evidence is required");
        StageBackbone generated = backboneGeneration.generate(new StageBackboneGenerationPort.Request(
                adventureId, sessionId, scenarioPackageId, evidence, preparationEvidence.storyPassages()));
        devLog("dev_initial_situation_prepare stage=backbone adventureId={} sessionId={} packageId={} sourceRefs={} requiredSituations={} backboneStages={}",
                adventureId.value(), sessionId.value(), scenarioPackageId, sourceReferences(evidence),
                requiredSituationAudit(generated.requiredSituations()),
                generated.stages().stream().map(stage -> stage.stageId() + ":order=" + stage.order()).toList());
        requirePackage(generated.scenarioPackageId(), scenarioPackageId);
        requireGrounding(generated.sourceRefs(), evidence, scenarioBacked, "backbone");
        generated.stages().forEach(stage -> requireGrounding(stage.sourceRefs(), evidence, scenarioBacked, "stage " + stage.stageId()));
        validateRequiredSourceRefs(generated.requiredSituations(), evidence);
        validateRequiredSituationStageAssignments(generated.stages(), generated.requiredSituations());
        StageBackbone backbone = generated;
        var first = backbone.stages().getFirst();
        List<StageDetailedGenerationPort.Request> detailedRequests = backbone.stages().stream().map(stage -> {
            List<RequiredSituation> assigned = backbone.requiredSituations().stream()
                    .filter(item -> stage.requiredSituationIds().contains(item.situationId())).toList();
            devLog("dev_initial_situation_prepare stage=detailed_request adventureId={} sessionId={} packageId={} stageId={} requiredSituations={} alreadyAssignedRequiredSituationIds={} openingRequired={}",
                    adventureId.value(), sessionId.value(), scenarioPackageId, stage.stageId(),
                    requiredSituationAudit(assigned), List.of(), stage.stageId().equals(first.stageId()));
            return new StageDetailedGenerationPort.Request(adventureId, sessionId, scenarioPackageId, stage.stageId(),
                    backbone.revision(), evidence, assigned, stage.stageId().equals(first.stageId()), List.of());
        }).toList();
        List<DetailedStage> effectiveStages = detailedGeneration.generateAll(detailedRequests);
        if (effectiveStages.size() != backbone.stages().size()) {
            throw new IllegalStateException("initial situation preparation returned an incomplete stage set");
        }
        for (int index = 0; index < effectiveStages.size(); index++) {
            DetailedStage detailed = effectiveStages.get(index);
            StageBackboneEntry stage = backbone.stages().get(index);
            devLog("dev_initial_situation_prepare stage=detailed_response adventureId={} sessionId={} packageId={} stageId={} situations={}",
                    adventureId.value(), sessionId.value(), scenarioPackageId, stage.stageId(), situationAudit(detailed.situations()));
            requirePackage(detailed.scenarioPackageId(), scenarioPackageId);
            if (!detailed.stageId().equals(stage.stageId()) || detailed.backboneRevision() != backbone.revision()) {
                throw new IllegalStateException("detailed stage does not match backbone stage");
            }
            validateDetailedStage(detailed, evidence, scenarioBacked, providedMapIds);
        }
        validateInitialSituationSet(effectiveStages, providedMapIds, backbone.stages(), backbone.requiredSituations());
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
        List<ScenarioSourceReference> evidence = backbone.sourceRefs();
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
                throw new IllegalStateException("required situation cites an unavailable RAG passage");
            }
        }
    }

    private static void validateRequiredSituationStageAssignments(List<StageBackboneEntry> stages,
            List<RequiredSituation> required) {
        java.util.Map<String, Integer> stageBySituation = new java.util.HashMap<>();
        for (StageBackboneEntry stage : stages) {
            for (String id : stage.requiredSituationIds()) {
                if (stageBySituation.putIfAbsent(id, stage.order()) != null) {
                    throw new IllegalStateException("required situation is assigned to multiple stages: " + id);
                }
            }
        }
        if (!stageBySituation.keySet().equals(required.stream().map(RequiredSituation::situationId)
                .collect(java.util.stream.Collectors.toSet()))) {
            throw new IllegalStateException("backbone must assign every required situation to exactly one stage");
        }
        for (RequiredSituation situation : required) {
            int order = stageBySituation.get(situation.situationId());
            for (String prerequisite : situation.prerequisiteSituationIds()) {
                Integer prerequisiteOrder = stageBySituation.get(prerequisite);
                if (prerequisiteOrder == null || prerequisiteOrder > order) {
                    throw new IllegalStateException("required situation prerequisite is assigned to a later stage");
                }
            }
        }
    }

    private static void validateInitialSituationSet(List<DetailedStage> stages, java.util.Set<UUID> providedMapIds,
            List<StageBackboneEntry> backboneStages, List<RequiredSituation> required) {
        java.util.Map<String, SituationDefinition> situations = new java.util.LinkedHashMap<>();
        java.util.Map<String, String> situationStages = new java.util.HashMap<>();
        java.util.Map<String, Integer> stageOrders = new java.util.HashMap<>();
        backboneStages.forEach(stage -> stageOrders.put(stage.stageId(), stage.order()));
        for (DetailedStage stage : stages) {
            for (SituationDefinition situation : stage.situations()) {
                SituationDefinition previous = situations.putIfAbsent(situation.situationId(), situation);
                if (previous != null) {
                    throw new IllegalStateException("situation ids must be unique across the initial set"
                            + " previousStageId=" + situationStages.get(situation.situationId())
                            + " previousProvenance=" + previous.provenance() + " previousRequired=" + previous.required()
                            + " currentStageId=" + stage.stageId() + " currentProvenance=" + situation.provenance()
                            + " currentRequired=" + situation.required());
                }
                situationStages.put(situation.situationId(), stage.stageId());
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
                devLog("dev_initial_situation_prepare stage=required_validation outcome=failed requiredSituationId={} expectedSourceRefs={} expectedPrerequisiteIds={} actualSituation={} allActualSituationIds={}",
                        item.situationId(), sourceReferences(item.sourceRefs()), item.prerequisiteSituationIds(),
                        actual == null ? "MISSING" : situationAudit(List.of(actual)), situations.keySet());
                throw new IllegalStateException("initial situation set does not preserve a required story situation");
            }
        }
        for (SituationDefinition actual : situations.values()) {
            if (actual.required() && actual.provenance() == SituationProvenance.SOURCE_EXTRACTED
                    && required.stream().noneMatch(item -> item.situationId().equals(actual.situationId()))
                    && !required.isEmpty()) {
                devLog("dev_initial_situation_prepare stage=required_validation outcome=failed reason=unextracted_required_situation actualSituation={}",
                        situationAudit(List.of(actual)));
                throw new IllegalStateException("initial situation set has a required story situation that was not extracted");
            }
        }
        for (SituationDefinition situation : situations.values()) {
            if (!situations.keySet().containsAll(situation.prerequisiteSituationIds())) {
                throw new IllegalStateException("situation prerequisite does not exist in the initial set");
            }
        }
        for (RequiredSituation item : required) {
            Integer dependentOrder = stageOrders.get(situationStages.get(item.situationId()));
            for (String prerequisiteId : item.prerequisiteSituationIds()) {
                Integer prerequisiteOrder = stageOrders.get(situationStages.get(prerequisiteId));
                if (dependentOrder == null || prerequisiteOrder == null) {
                    throw new IllegalStateException("required situation prerequisite has no initial stage");
                }
                if (prerequisiteOrder > dependentOrder) {
                    throw new IllegalStateException("required situation prerequisite must be in the same or an earlier stage");
                }
            }
        }
        devLog("dev_initial_situation_prepare stage=required_validation outcome=passed requiredSituationIds={} situationCount={} intermediateCandidateCount={}",
                required.stream().map(RequiredSituation::situationId).toList(), situations.size(), intermediateCandidates);
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

    private static void devLog(String pattern, Object... args) {
        if (com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.enabled()) LOGGER.info(pattern, args);
    }

    private static List<String> requiredSituationAudit(List<RequiredSituation> situations) {
        return situations.stream().map(item -> item.situationId() + ":prerequisites=" + item.prerequisiteSituationIds()
                + ":sourceRefs=" + sourceReferences(item.sourceRefs())).toList();
    }

    private static List<String> situationAudit(List<SituationDefinition> situations) {
        return situations.stream().map(item -> item.situationId() + ":required=" + item.required() + ":provenance="
                + item.provenance() + ":prerequisites=" + item.prerequisiteSituationIds() + ":sourceRefs="
                + sourceReferences(item.sourceRefs())).toList();
    }

    private static List<String> sourceReferences(List<ScenarioSourceReference> refs) {
        return refs.stream().map(ref -> ref.knowledgeDocumentId().value() + ":v" + ref.extractionVersion()
                + ":" + ref.locator()).toList();
    }

    public record Result(StageBackbone backbone, List<DetailedStage> detailedStages, DetailedStage currentStage,
            SituationDefinition openingSituation) {
        public Result { detailedStages = List.copyOf(detailedStages); }
    }
}
