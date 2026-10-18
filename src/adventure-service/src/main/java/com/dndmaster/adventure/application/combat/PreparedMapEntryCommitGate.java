package com.dndmaster.adventure.application.combat;

import com.dndmaster.adventure.application.runtime.RuntimeTurn;
import com.dndmaster.adventure.application.runtime.RuntimeTurnCommitGate;
import com.dndmaster.adventure.domain.adventure.Adventure;
import com.dndmaster.adventure.domain.adventure.AdventurePartyMember;
import com.dndmaster.adventure.application.runtime.RuntimeEvidenceType;
import com.dndmaster.adventure.domain.scenario.ScenarioPackage;
import com.dndmaster.adventure.application.scenario.compilation.ScenarioPackageRepository;
import com.dndmaster.adventure.domain.scenario.StageArtifactRepository;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

/** Ensures a prepared map accepts the party's entry position before turn persistence. */
public final class PreparedMapEntryCommitGate implements RuntimeTurnCommitGate {
    private final CombatMapViewPort mapView;
    private final CombatMapPreparationPort mapPreparation;
    private final ScenarioPackageRepository packageRepository;
    private final StageArtifactRepository stageArtifactRepository;

    public PreparedMapEntryCommitGate(CombatMapViewPort mapView, CombatMapPreparationPort mapPreparation) {
        this(mapView, mapPreparation, null, null);
    }

    public PreparedMapEntryCommitGate(CombatMapViewPort mapView, CombatMapPreparationPort mapPreparation,
            ScenarioPackageRepository packageRepository, StageArtifactRepository stageArtifactRepository) {
        this.mapView = Objects.requireNonNull(mapView);
        this.mapPreparation = Objects.requireNonNull(mapPreparation);
        this.packageRepository = packageRepository;
        this.stageArtifactRepository = stageArtifactRepository;
    }

    @Override
    public void beforeCommit(Adventure adventure, RuntimeTurn turn) {
        String destinationScene = adventure.currentContext().currentScene();
        boolean sceneChanged = turn != null && !blank(destinationScene).isBlank()
                && !destinationScene.equalsIgnoreCase(blank(turn.context().currentScene()));
        boolean enteredMap = turn != null && (turn.plan().mapEntryRequested() || turn.plan().combatStartRequested());
        String selectedSituationId = turn == null || turn.pendingState() == null ? null : turn.pendingState().selectedSituationId();
        boolean enteredSituation = selectedSituationId != null;
        var situation = adventure.currentSituation();
        var storyState = adventure.storyRuntimeState();
        boolean openingSituation = turn != null && turn.conversation().isEmpty() && storyState != null;
        if (!enteredMap && !sceneChanged && !enteredSituation && !openingSituation) return;
        var target = storyState == null || packageRepository == null || stageArtifactRepository == null
                ? null : stageArtifactRepository.findDetailedStage(adventure.id(), adventure.lockedScenarioPackageId(),
                        storyState.stageId(), storyState.backboneRevision(), storyState.detailedStageRevision())
                        .stream().flatMap(stage -> stage.situations().stream())
                        .filter(candidate -> candidate.situationId().equals(storyState.activeSituationId()))
                        .filter(candidate -> selectedSituationId == null || candidate.situationId().equals(selectedSituationId))
                        .findFirst().orElse(null);
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
            ScenarioPackage scenarioPackage = packageRepository.findById(adventure.lockedScenarioPackageId())
                    .orElseThrow(() -> new IllegalStateException("scenario package unavailable for situation map entry"));
            var map = scenarioPackage.mapDefinitions().stream().filter(candidate -> candidate.id().equals(target.mapDefinitionId()))
                    .filter(com.dndmaster.adventure.domain.scenario.MapDefinition::autoActivatable).findFirst()
                    .orElseThrow(() -> new IllegalStateException("map-needed situation has no usable map definition"));
            var draftContext = entryContext(adventure, turn, destinationScene);
            String context = "adventureId=" + adventure.id().value() + " situationId=" + target.situationId()
                    + " mapDefinitionId=" + map.id() + " entryEvidenceChars=" + draftContext.entryEvidence().length();
            com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.measure(
                    org.slf4j.LoggerFactory.getLogger(getClass()), "situation_map_entry_prepare", context,
                    () -> mapPreparation.prepareDraft(adventure.id(), adventure.ownerPlayerId().value(), adventure.ruleSetId(), map, draftContext));
        } else if (!mapView.hasPreparedMap(adventure.id().value(), adventure.ownerPlayerId().value())) return;
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
        var context = new CombatMapPreparationPort.ActivationContext(playerTokenId, situation.situationId(),
                situation.revision(), adventure.turnIndex(), destinationScene, location, null, null, evidence.toString());
        String activationContext = "adventureId=" + adventure.id().value() + " situationId=" + situation.situationId()
                + " sceneChars=" + blank(destinationScene).length() + " entryEvidenceChars=" + evidence.length();
        com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.measure(
                org.slf4j.LoggerFactory.getLogger(getClass()), "situation_map_entry_activate", activationContext,
                () -> mapPreparation.activatePrepared(adventure.id(), adventure.ownerPlayerId().value(), adventure.ruleSetId(), 1, context));
    }

    private static CombatMapPreparationPort.ActivationContext entryContext(Adventure adventure, RuntimeTurn turn,
            String location) {
        String narration = turn == null ? "" : blank(turn.narration());
        return new CombatMapPreparationPort.ActivationContext(null, adventure.currentSituation().situationId(),
                adventure.currentSituation().revision(), adventure.turnIndex(), adventure.currentContext().currentScene(),
                location, null, null, "FIRST_NARRATION=" + narration);
    }

    private static String blank(String value) { return value == null ? "" : value; }
}
