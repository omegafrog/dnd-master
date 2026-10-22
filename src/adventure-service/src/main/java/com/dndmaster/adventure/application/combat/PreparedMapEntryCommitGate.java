package com.dndmaster.adventure.application.combat;

import com.dndmaster.adventure.application.runtime.RuntimeTurn;
import com.dndmaster.adventure.application.runtime.RuntimeTurnCommitGate;
import com.dndmaster.adventure.domain.adventure.Adventure;
import com.dndmaster.adventure.domain.adventure.AdventurePartyMember;
import com.dndmaster.adventure.application.runtime.RuntimeEvidenceType;
import com.dndmaster.adventure.domain.scenario.ScenarioPackage;
import com.dndmaster.adventure.application.scenario.compilation.ScenarioPackageRepository;
import com.dndmaster.adventure.domain.scenario.StageArtifactRepository;
import com.dndmaster.adventure.application.scenario.ScenarioBundleRepository;
import com.dndmaster.adventure.application.scenario.compilation.ScenarioSourceExcerptPort;
import com.dndmaster.adventure.application.scenario.compilation.ResolutionExtractionPort;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

/** Ensures a prepared map accepts the party's entry position before turn persistence. */
public final class PreparedMapEntryCommitGate implements RuntimeTurnCommitGate {
    private final CombatMapViewPort mapView;
    private final CombatMapPreparationPort mapPreparation;
    private final ScenarioPackageRepository packageRepository;
    private final StageArtifactRepository stageArtifactRepository;
    private final ScenarioBundleRepository bundleRepository;
    private final ScenarioSourceExcerptPort sourceExcerptPort;

    public PreparedMapEntryCommitGate(CombatMapViewPort mapView, CombatMapPreparationPort mapPreparation) {
        this(mapView, mapPreparation, null, null);
    }

    public PreparedMapEntryCommitGate(CombatMapViewPort mapView, CombatMapPreparationPort mapPreparation,
            ScenarioPackageRepository packageRepository, StageArtifactRepository stageArtifactRepository) {
        this(mapView, mapPreparation, packageRepository, stageArtifactRepository, null, null);
    }

    public PreparedMapEntryCommitGate(CombatMapViewPort mapView, CombatMapPreparationPort mapPreparation,
            ScenarioPackageRepository packageRepository, StageArtifactRepository stageArtifactRepository,
            ScenarioBundleRepository bundleRepository, ScenarioSourceExcerptPort sourceExcerptPort) {
        this.mapView = Objects.requireNonNull(mapView);
        this.mapPreparation = Objects.requireNonNull(mapPreparation);
        this.packageRepository = packageRepository;
        this.stageArtifactRepository = stageArtifactRepository;
        this.bundleRepository = bundleRepository;
        this.sourceExcerptPort = sourceExcerptPort;
    }

    @Override
    public void beforeCommit(Adventure adventure, RuntimeTurn turn) {
        beforeCommit(adventure, turn, null);
    }

    @Override
    public void beforeCommit(Adventure adventure, RuntimeTurn turn,
            com.dndmaster.adventure.domain.runtime.CurrentSituation previousSituation) {
        String destinationScene = adventure.currentContext().currentScene();
        boolean sceneChanged = turn != null && !blank(destinationScene).isBlank()
                && !destinationScene.equalsIgnoreCase(blank(turn.context().currentScene()));
        boolean enteredMap = turn != null && (turn.plan().mapEntryRequested() || turn.plan().combatStartRequested());
        String selectedSituationId = turn == null || turn.pendingState() == null ? null : turn.pendingState().selectedSituationId();
        boolean enteredSituation = selectedSituationId != null;
        var pendingSituation = turn == null || turn.pendingState() == null ? null : turn.pendingState().situation();
        var situation = pendingSituation == null ? adventure.currentSituation() : pendingSituation;
        boolean newSituation = pendingSituation != null && selectedSituationId == null
                && previousSituation != null
                && !situation.situationId().equals(previousSituation.situationId());
        var storyState = adventure.storyRuntimeState();
        boolean openingSituation = turn != null && turn.conversation().isEmpty() && storyState != null;
        boolean situationDriven = storyState != null && packageRepository != null && stageArtifactRepository != null;
        if (!situationDriven && !enteredMap && !sceneChanged && !enteredSituation && !openingSituation) return;
        boolean matchesPreparedSituation = storyState != null && situation != null
                && (selectedSituationId != null ? selectedSituationId.equals(storyState.activeSituationId())
                        : (openingSituation || sceneChanged) && storyState.activeSituationId() != null
                                || situation.situationId().toString().equals(storyState.activeSituationId()));
        boolean repairMissingMap = situationDriven && !newSituation && !matchesPreparedSituation
                && situation.mapDefinitionId() != null
                && mapView.playerView(adventure.id().value(), adventure.ownerPlayerId().value()).isEmpty()
                && mapView.hasPreparedMap(adventure.id().value(), adventure.ownerPlayerId().value());
        if (situationDriven && enteredSituation && !newSituation && !matchesPreparedSituation) {
            String expectedSituationId = selectedSituationId;
            if (com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.enabled()) {
                org.slf4j.LoggerFactory.getLogger(getClass()).error(
                        "dev_flow operation=situation_map_entry outcome=failed adventureId={} stageId={} expectedSituationId={} actualSituationId=null expectedMapDefinitionId=unknown actualMapDefinitionId=null recoveryAction=reject_turn failureClass=MissingSituationDefinition",
                        adventure.id().value(), storyState.stageId(), expectedSituationId);
            }
            throw new IllegalStateException("destination situation is unavailable for map entry: " + expectedSituationId);
        }
        if (situationDriven && !newSituation && !matchesPreparedSituation && !repairMissingMap) {
            com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.measure(
                    org.slf4j.LoggerFactory.getLogger(getClass()), "situation_map_entry_preserve",
                    "adventureId=" + adventure.id().value() + " situationId=" + situation.situationId()
                            + " activePreparedSituationId=" + storyState.activeSituationId(), () -> null);
            if (com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.enabled()) {
                org.slf4j.LoggerFactory.getLogger(getClass()).info(
                        "dev_flow operation=situation_map_entry outcome=preserved_current_situation situationId={} activePreparedSituationId={} mapDefinitionId={} recoveryAction=keep_current_map_binding",
                        situation.situationId(), storyState.activeSituationId(), situation.mapDefinitionId());
            }
            return;
        }
        var target = newSituation || repairMissingMap || !matchesPreparedSituation || storyState == null
                || packageRepository == null || stageArtifactRepository == null
                ? null : stageArtifactRepository.findDetailedStage(adventure.id(), adventure.lockedScenarioPackageId(),
                        storyState.stageId(), storyState.backboneRevision(), storyState.detailedStageRevision())
                        .stream().flatMap(stage -> stage.situations().stream())
                        .filter(candidate -> candidate.situationId().equals(storyState.activeSituationId()))
                        .filter(candidate -> selectedSituationId == null || candidate.situationId().equals(selectedSituationId))
                        .findFirst().orElse(null);
        com.dndmaster.adventure.domain.scenario.MapDefinition targetMap = null;
        if (situationDriven && target == null && (newSituation || repairMissingMap)) {
            if (situation.mapDefinitionId() == null) {
                com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.measure(
                        org.slf4j.LoggerFactory.getLogger(getClass()), "situation_map_entry_deactivate",
                        "adventureId=" + adventure.id().value() + " situationId=" + situation.situationId() + " mapDefinitionId=null",
                        () -> mapPreparation.deactivateForMaplessSituation(adventure.id(), adventure.ownerPlayerId().value()));
                return;
            }
            ScenarioPackage scenarioPackage = packageRepository.findById(adventure.lockedScenarioPackageId()).orElse(null);
            if (scenarioPackage == null) throw mapEntryFailure(adventure, situation.situationId().toString(), situation.mapDefinitionId(),
                    "scenario_package_unavailable", "scenario package unavailable for situation map entry");
            targetMap = scenarioPackage.mapDefinitions().stream()
                    .filter(candidate -> candidate.id().equals(situation.mapDefinitionId()))
                    .filter(com.dndmaster.adventure.domain.scenario.MapDefinition::autoActivatable).findFirst().orElse(null);
            if (targetMap == null) throw mapEntryFailure(adventure, situation.situationId().toString(), situation.mapDefinitionId(),
                    "usable_map_unavailable", "new situation references a map that cannot be activated");
            if (com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.enabled()) {
                org.slf4j.LoggerFactory.getLogger(getClass()).info(
                        "dev_flow operation=situation_map_entry outcome={} adventureId={} situationId={} expectedMapDefinitionId={} actualMapDefinitionId={} recoveryAction=activate_provided_map",
                        newSituation ? "new_situation_map" : "recovered_situation_map",
                        adventure.id().value(), situation.situationId(), situation.mapDefinitionId(), targetMap.id());
            }
        } else if (situationDriven && target == null && matchesPreparedSituation) {
            if (selectedSituationId != null || storyState.activeSituationId() != null || openingSituation) {
                String expectedSituationId = selectedSituationId != null ? selectedSituationId
                        : openingSituation ? "opening" : storyState.activeSituationId();
                if (com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.enabled()) {
                    org.slf4j.LoggerFactory.getLogger(getClass()).error(
                            "dev_flow operation=situation_map_entry outcome=failed adventureId={} stageId={} expectedSituationId={} actualSituationId=null expectedMapDefinitionId=unknown actualMapDefinitionId=null recoveryAction=reject_turn failureClass=MissingSituationDefinition",
                            adventure.id().value(), storyState.stageId(), expectedSituationId);
                }
                throw new IllegalStateException("destination situation is unavailable for map entry: " + expectedSituationId);
            }
            com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.measure(
                    org.slf4j.LoggerFactory.getLogger(getClass()), "situation_map_entry_deactivate",
                    "adventureId=" + adventure.id().value() + " situationId=null mapDefinitionId=null",
                    () -> mapPreparation.deactivateForMaplessSituation(adventure.id(), adventure.ownerPlayerId().value()));
            return;
        }
        if (target != null && !target.mapNeeded()) {
            String context = "adventureId=" + adventure.id().value() + " situationId=" + target.situationId()
                    + " mapNeeded=false sceneChars=" + blank(destinationScene).length();
            com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.measure(
                    org.slf4j.LoggerFactory.getLogger(getClass()), "situation_map_entry_deactivate", context,
                    () -> mapPreparation.deactivateForMaplessSituation(adventure.id(), adventure.ownerPlayerId().value()));
            if (com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.enabled()) {
                org.slf4j.LoggerFactory.getLogger(getClass()).info(
                        "dev_flow operation=situation_map_entry outcome=mapless situationId={} expectedMapNeeded=false actualMapDefinitionId=null recoveryAction=continue_without_map",
                        target.situationId());
            }
            return;
        }
        if (target != null) {
            ScenarioPackage scenarioPackage = packageRepository.findById(adventure.lockedScenarioPackageId()).orElse(null);
            if (scenarioPackage == null) throw mapEntryFailure(adventure, target.situationId(), target.mapDefinitionId(),
                    "scenario_package_unavailable", "scenario package unavailable for situation map entry");
            var map = scenarioPackage.mapDefinitions().stream().filter(candidate -> candidate.id().equals(target.mapDefinitionId()))
                    .filter(com.dndmaster.adventure.domain.scenario.MapDefinition::autoActivatable).findFirst()
                    .orElse(null);
            if (map == null) throw mapEntryFailure(adventure, target.situationId(), target.mapDefinitionId(),
                    "usable_map_unavailable", "map-needed situation has no usable map definition");
            targetMap = map;
        } else if (targetMap == null && !mapView.hasPreparedMap(adventure.id().value(), adventure.ownerPlayerId().value())) return;
        UUID playerTokenId = adventure.party().stream().findFirst()
                .map(AdventurePartyMember::characterSheetId)
                .map(sheet -> UUID.nameUUIDFromBytes(("player-" + sheet.value()).getBytes(StandardCharsets.UTF_8)))
                .orElse(null);
        StringBuilder evidence = new StringBuilder("FIRST_NARRATION=").append(turn == null ? blank(situation.firstNarration()) : blank(turn.narration()));
        if (turn != null) {
            evidence.append("\nPLAYER_ACTION=").append(blank(turn.action()))
                    .append("\nGM_JUDGMENT=").append(blank(turn.plan().judgment()))
                    .append("\nGM_NARRATION=").append(blank(turn.narration()));
            turn.plan().citedEvidence().stream()
                    .filter(item -> item.evidenceType() == RuntimeEvidenceType.STORYBOOK)
                    .map(com.dndmaster.adventure.application.runtime.RuntimeEvidence::excerpt)
                    .filter(value -> value != null && !value.isBlank())
                    .distinct()
                    .forEach(value -> evidence.append("\nSTORYBOOK_EVIDENCE=").append(value));
        }
        String location = sceneChanged && !enteredMap ? destinationScene : situation.location();
        ScenarioPackage encounterPackage = packageRepository == null ? null
                : packageRepository.findById(adventure.lockedScenarioPackageId()).orElse(null);
        var mappedMap = targetMap;
        var mappedSituations = storyState == null || stageArtifactRepository == null || mappedMap == null ? java.util.List.<com.dndmaster.adventure.domain.scenario.SituationDefinition>of()
                : stageArtifactRepository.findDetailedStages(adventure.id(), adventure.lockedScenarioPackageId(), storyState.backboneRevision())
                        .stream().flatMap(stage -> stage.situations().stream())
                        .filter(candidate -> mappedMap.id().equals(candidate.mapDefinitionId())).toList();
        if (mappedSituations.stream().anyMatch(candidate -> candidate.combatScenarioIds() == null)) {
            throw mapEntryFailure(adventure, situation.situationId().toString(), mappedMap.id(),
                    "encounter_association_legacy", "map situation has no explicit combat scenario association");
        }
        var scenarioIds = mappedSituations.stream().flatMap(candidate -> candidate.combatScenarioIds().stream()).distinct().toList();
        var scenarioEncounters = encounterPackage == null || encounterPackage.scenarioModel() == null ? java.util.List.<com.dndmaster.adventure.domain.scenario.CombatScenarioDefinition>of()
                : encounterPackage.scenarioModel().combatScenarios();
        var encountersById = scenarioEncounters.stream().collect(java.util.stream.Collectors.toMap(
                com.dndmaster.adventure.domain.scenario.CombatScenarioDefinition::scenarioId, candidate -> candidate));
        if (mappedMap != null && !encountersById.keySet().containsAll(scenarioIds)) throw mapEntryFailure(adventure,
                situation.situationId().toString(), mappedMap.id(), "encounter_association_invalid",
                "mapped situation references an unknown combat scenario");
        var sourceExcerpts = loadEncounterExcerpts(encounterPackage, scenarioEncounters, adventure);
        var encounterRequirements = scenarioIds.stream().map(encountersById::get).map(encounter ->
                        new com.dndmaster.adventure.application.scenario.preparation.ScenarioSpatialFeaturePlacementModelPort.EncounterRequirement(
                                encounter.scenarioId(), encounter.enemyKey(), encounter.displayName(), encounter.count(), true,
                                encounter.sourceRefs().stream().map(PreparedMapEntryCommitGate::sourceIdentity).toList(),
                                sourceExcerpts.getOrDefault(encounter.scenarioId(), java.util.List.of()))).toList();
        var context = new CombatMapPreparationPort.ActivationContext(playerTokenId, situation.situationId(),
                situation.revision(), adventure.turnIndex(), destinationScene, location, null, null, evidence.toString(), encounterRequirements,
                targetMap == null ? "" : targetMap.assetId() + "@" + targetMap.assetLocator() + "|grid=" + targetMap.grid()
                        + "|walls=" + targetMap.walls() + "|doors=" + targetMap.doors() + "|obstacles=" + targetMap.obstacles());
        String activationContext = "adventureId=" + adventure.id().value() + " situationId=" + situation.situationId()
                + " sceneChars=" + blank(destinationScene).length() + " entryEvidenceChars=" + evidence.length();
        var mapDefinition = targetMap;
        com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.measure(
                org.slf4j.LoggerFactory.getLogger(getClass()), "situation_map_entry_activate", activationContext
                        + " mapDefinitionId=" + (mapDefinition == null ? null : mapDefinition.id()),
                () -> {
                    if (mapDefinition == null) mapPreparation.activatePrepared(adventure.id(), adventure.ownerPlayerId().value(), adventure.ruleSetId(), 1, context);
                    else mapPreparation.prepareInitial(adventure.id(), adventure.ownerPlayerId().value(), adventure.ruleSetId(), mapDefinition, 1, context);
                });
    }

    private IllegalStateException mapEntryFailure(Adventure adventure, String situationId, UUID mapDefinitionId,
            String failureClass, String message) {
        if (com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.enabled()) {
            org.slf4j.LoggerFactory.getLogger(getClass()).error(
                    "dev_flow operation=situation_map_entry outcome=failed adventureId={} situationId={} expectedMapDefinitionId={} actualMapDefinitionId=null failureClass={} recoveryAction=reject_turn",
                    adventure.id().value(), situationId, mapDefinitionId, failureClass);
        }
        return new IllegalStateException(message);
    }

    private static String blank(String value) { return value == null ? "" : value; }
    private java.util.Map<String, java.util.List<String>> loadEncounterExcerpts(ScenarioPackage scenarioPackage,
            java.util.List<com.dndmaster.adventure.domain.scenario.CombatScenarioDefinition> encounters, Adventure adventure) {
        if (encounters.isEmpty()) return java.util.Map.of();
        if (bundleRepository == null || sourceExcerptPort == null) throw mapEntryFailure(adventure,
                adventure.currentSituation() == null ? "unknown" : adventure.currentSituation().situationId().toString(),
                null, "encounter_source_unavailable", "source excerpts are unavailable for mapped encounters");
        var bundle = bundleRepository.findById(scenarioPackage.bundleId()).orElseThrow(() -> mapEntryFailure(adventure,
                adventure.currentSituation() == null ? "unknown" : adventure.currentSituation().situationId().toString(),
                null, "encounter_source_unavailable", "source bundle is unavailable for mapped encounters"));
        var excerpts = sourceExcerptPort.load(bundle);
        java.util.Map<String, java.util.List<String>> result = new java.util.HashMap<>();
        for (var encounter : encounters) {
            var matching = excerpts.stream().filter(ResolutionExtractionPort.SourceExcerpt::isPublishedEvidence)
                    .filter(excerpt -> encounter.sourceRefs().stream().anyMatch(reference ->
                            reference.knowledgeDocumentId().equals(excerpt.documentId())
                                    && reference.extractionVersion() == excerpt.extractionVersion()
                                    && reference.locator().equals(excerpt.locator())))
                    .map(ResolutionExtractionPort.SourceExcerpt::text).filter(text -> text != null && !text.isBlank()).toList();
            if (matching.isEmpty()) throw mapEntryFailure(adventure,
                    adventure.currentSituation() == null ? "unknown" : adventure.currentSituation().situationId().toString(),
                    null, "encounter_source_excerpt_unavailable", "source excerpt is unavailable for mapped encounter");
            result.put(encounter.scenarioId(), matching);
        }
        return java.util.Map.copyOf(result);
    }

    private static String sourceIdentity(com.dndmaster.adventure.domain.scenario.ScenarioSourceReference reference) {
        return reference.knowledgeDocumentId().value() + ":" + reference.extractionVersion() + ":" + reference.locator();
    }
}
