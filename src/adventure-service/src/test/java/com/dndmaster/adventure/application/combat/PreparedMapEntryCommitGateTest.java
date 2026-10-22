package com.dndmaster.adventure.application.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dndmaster.adventure.application.runtime.RuntimePlan;
import com.dndmaster.adventure.application.runtime.RuntimeTurn;
import com.dndmaster.adventure.domain.adventure.Adventure;
import com.dndmaster.adventure.domain.adventure.AdventureContext;
import com.dndmaster.adventure.domain.adventure.ConversationEntry;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import com.dndmaster.adventure.domain.adventure.RuleSetId;
import com.dndmaster.adventure.domain.runtime.CurrentSituation;
import com.dndmaster.adventure.domain.runtime.PendingRuntimeState;
import com.dndmaster.adventure.domain.scenario.MapDefinition;
import com.dndmaster.adventure.domain.scenario.ScenarioPackage;
import com.dndmaster.adventure.domain.scenario.SituationDefinition;
import com.dndmaster.adventure.domain.scenario.StageArtifactRepository;
import com.dndmaster.adventure.application.scenario.compilation.ScenarioPackageRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;
import com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics;
import ch.qos.logback.classic.spi.ILoggingEvent;

class PreparedMapEntryCommitGateTest {
    private final Logger logger = (Logger) LoggerFactory.getLogger(PreparedMapEntryCommitGate.class);
    private ListAppender<ILoggingEvent> diagnostics;
    private String previousDiagnosticsFlag;

    @BeforeEach
    void enableDiagnosticCapture() {
        previousDiagnosticsFlag = System.getProperty(DevelopmentDiagnostics.FLAG);
        System.setProperty(DevelopmentDiagnostics.FLAG, "true");
        diagnostics = new ListAppender<>();
        diagnostics.start();
        logger.addAppender(diagnostics);
    }

    @AfterEach
    void restoreDiagnosticCapture() {
        logger.detachAppender(diagnostics);
        if (previousDiagnosticsFlag == null) System.clearProperty(DevelopmentDiagnostics.FLAG);
        else System.setProperty(DevelopmentDiagnostics.FLAG, previousDiagnosticsFlag);
    }

    @Test
    void deactivates_the_previous_map_when_the_destination_situation_is_mapless() {
        Adventure adventure = adventureAt("road");
        UUID packageId = UUID.randomUUID();
        when(adventure.lockedScenarioPackageId()).thenReturn(packageId);
        var story = mock(com.dndmaster.adventure.domain.runtime.story.StoryRuntimeState.class);
        when(adventure.storyRuntimeState()).thenReturn(story);
        when(story.activeSituationId()).thenReturn("road-entry");
        when(story.stageId()).thenReturn("stage-1");
        when(story.backboneRevision()).thenReturn(1L);
        when(story.detailedStageRevision()).thenReturn(1L);
        var situation = mock(SituationDefinition.class);
        when(situation.situationId()).thenReturn("road-entry");
        when(situation.mapNeeded()).thenReturn(false);
        var stage = mock(com.dndmaster.adventure.domain.scenario.DetailedStage.class);
        when(stage.situations()).thenReturn(List.of(situation));
        StageArtifactRepository artifacts = mock(StageArtifactRepository.class);
        when(artifacts.findDetailedStage(any(), eq(packageId), eq("stage-1"), eq(1L), eq(1L)))
                .thenReturn(java.util.Optional.of(stage));
        ScenarioPackageRepository packages = mock(ScenarioPackageRepository.class);
        CombatMapPreparationPort mapPreparation = mock(CombatMapPreparationPort.class);

        new PreparedMapEntryCommitGate(mock(CombatMapViewPort.class), mapPreparation, packages, artifacts)
                .beforeCommit(adventure, turnFrom("cellar", false, false));

        AdventureId adventureId = adventure.id();
        UUID ownerId = adventure.ownerPlayerId().value();
        verify(mapPreparation).deactivateForMaplessSituation(adventureId, ownerId);
        verify(mapPreparation, never()).activatePrepared(any(), any(), any(), any(int.class), any());
        assertTrue(diagnostics.list.stream().map(ILoggingEvent::getFormattedMessage)
                .anyMatch(message -> message.contains("situation_map_entry")
                        && message.contains("outcome=mapless")
                        && message.contains("situationId=road-entry")
                        && message.contains("expectedMapNeeded=false")
                        && message.contains("actualMapDefinitionId=null")));
    }

    @Test
    void activates_the_provided_map_for_a_new_intermediate_situation_without_changing_required_progress() {
        Adventure adventure = adventureAt("cellar");
        UUID packageId = UUID.randomUUID();
        UUID mapId = UUID.randomUUID();
        when(adventure.lockedScenarioPackageId()).thenReturn(packageId);
        var story = mock(com.dndmaster.adventure.domain.runtime.story.StoryRuntimeState.class);
        when(adventure.storyRuntimeState()).thenReturn(story);
        when(story.activeSituationId()).thenReturn("opening");
        when(story.stageId()).thenReturn("stage-1");
        when(story.backboneRevision()).thenReturn(1L);
        when(story.detailedStageRevision()).thenReturn(1L);
        MapDefinition map = mock(MapDefinition.class);
        when(map.id()).thenReturn(mapId);
        when(map.autoActivatable()).thenReturn(true);
        ScenarioPackage scenarioPackage = mock(ScenarioPackage.class);
        when(scenarioPackage.mapDefinitions()).thenReturn(List.of(map));
        ScenarioPackageRepository packages = mock(ScenarioPackageRepository.class);
        when(packages.findById(packageId)).thenReturn(java.util.Optional.of(scenarioPackage));
        StageArtifactRepository artifacts = mock(StageArtifactRepository.class);
        CurrentSituation newScene = new CurrentSituation(UUID.randomUUID(), 1, "뒤뜰", "정찰병 순찰 중", "발각 위험",
                "정찰병을 살핀다", null, null, "고블린 정찰병이 순찰 중", "고블린 정찰병 하나가 뒤뜰을 순찰한다.", mapId);
        PendingRuntimeState pending = mock(PendingRuntimeState.class);
        when(pending.selectedSituationId()).thenReturn(null);
        when(pending.situation()).thenReturn(newScene);
        RuntimeTurn turn = turnFrom("cellar", false, false);
        when(turn.pendingState()).thenReturn(pending);
        CombatMapPreparationPort mapPreparation = mock(CombatMapPreparationPort.class);

        new PreparedMapEntryCommitGate(mock(CombatMapViewPort.class), mapPreparation, packages, artifacts)
                .beforeCommit(adventure, turn, adventure.currentSituation());

        AdventureId adventureId = adventure.id();
        UUID ownerId = adventure.ownerPlayerId().value();
        RuleSetId ruleSetId = adventure.ruleSetId();
        verify(mapPreparation).prepareInitial(eq(adventureId), eq(ownerId), eq(ruleSetId), eq(map), eq(1), any());
        assertTrue(diagnostics.list.stream().map(ILoggingEvent::getFormattedMessage)
                .anyMatch(message -> message.contains("outcome=new_situation_map")
                        && message.contains("expectedMapDefinitionId=" + mapId)
                        && message.contains("actualMapDefinitionId=" + mapId)));
    }

    @Test
    void prepares_the_active_situations_map_and_entry_narration_before_committing_its_scene() {
        Adventure adventure = adventureAt("cellar");
        UUID packageId = UUID.randomUUID();
        UUID mapId = UUID.randomUUID();
        when(adventure.lockedScenarioPackageId()).thenReturn(packageId);
        var story = mock(com.dndmaster.adventure.domain.runtime.story.StoryRuntimeState.class);
        when(adventure.storyRuntimeState()).thenReturn(story);
        when(story.activeSituationId()).thenReturn("cellar-entry");
        when(story.stageId()).thenReturn("stage-1");
        when(story.backboneRevision()).thenReturn(1L);
        when(story.detailedStageRevision()).thenReturn(1L);
        var situation = mock(SituationDefinition.class);
        when(situation.situationId()).thenReturn("cellar-entry");
        when(situation.mapNeeded()).thenReturn(true);
        when(situation.mapDefinitionId()).thenReturn(mapId);
        var stage = mock(com.dndmaster.adventure.domain.scenario.DetailedStage.class);
        when(stage.situations()).thenReturn(List.of(situation));
        StageArtifactRepository artifacts = mock(StageArtifactRepository.class);
        when(artifacts.findDetailedStage(any(), eq(packageId), eq("stage-1"), eq(1L), eq(1L)))
                .thenReturn(java.util.Optional.of(stage));
        MapDefinition map = mock(MapDefinition.class);
        when(map.id()).thenReturn(mapId);
        when(map.autoActivatable()).thenReturn(true);
        ScenarioPackage scenarioPackage = mock(ScenarioPackage.class);
        when(scenarioPackage.mapDefinitions()).thenReturn(List.of(map));
        ScenarioPackageRepository packages = mock(ScenarioPackageRepository.class);
        when(packages.findById(packageId)).thenReturn(java.util.Optional.of(scenarioPackage));
        RuntimeTurn turn = turnFrom("cellar", false, false);
        PendingRuntimeState pending = mock(PendingRuntimeState.class);
        when(pending.selectedSituationId()).thenReturn("cellar-entry");
        when(turn.pendingState()).thenReturn(pending);
        CombatMapViewPort mapView = mock(CombatMapViewPort.class);
        CombatMapPreparationPort mapPreparation = mock(CombatMapPreparationPort.class);

        new PreparedMapEntryCommitGate(mapView, mapPreparation, packages, artifacts).beforeCommit(adventure, turn);

        AdventureId adventureId = adventure.id();
        UUID ownerId = adventure.ownerPlayerId().value();
        RuleSetId ruleSetId = adventure.ruleSetId();

        ArgumentCaptor<CombatMapPreparationPort.ActivationContext> context =
                ArgumentCaptor.forClass(CombatMapPreparationPort.ActivationContext.class);
        verify(mapPreparation, times(1)).prepareInitial(eq(adventureId), eq(ownerId), eq(ruleSetId), eq(map), eq(1), context.capture());
        assertEquals(true, context.getValue().entryEvidence().contains("저장고 안에 도착했다."));
        assertTrue(diagnostics.list.stream().map(ILoggingEvent::getFormattedMessage)
                .anyMatch(message -> message.contains("operation=situation_map_entry_activate")
                        && message.contains("outcome=completed")
                        && message.contains("mapDefinitionId=" + mapId)
                        && message.contains("mapDefinitionId=" + mapId)
                        && message.contains("entryEvidenceChars=")));
    }

    @Test
    void rejects_an_unresolved_destination_instead_of_activating_a_previously_prepared_map() {
        Adventure adventure = adventureAt("cellar");
        UUID packageId = UUID.randomUUID();
        when(adventure.lockedScenarioPackageId()).thenReturn(packageId);
        var story = mock(com.dndmaster.adventure.domain.runtime.story.StoryRuntimeState.class);
        when(adventure.storyRuntimeState()).thenReturn(story);
        when(story.activeSituationId()).thenReturn("current-entry");
        when(story.stageId()).thenReturn("stage-1");
        when(story.backboneRevision()).thenReturn(1L);
        when(story.detailedStageRevision()).thenReturn(1L);
        var current = mock(SituationDefinition.class);
        when(current.situationId()).thenReturn("current-entry");
        var stage = mock(com.dndmaster.adventure.domain.scenario.DetailedStage.class);
        when(stage.situations()).thenReturn(List.of(current));
        StageArtifactRepository artifacts = mock(StageArtifactRepository.class);
        when(artifacts.findDetailedStage(any(), eq(packageId), eq("stage-1"), eq(1L), eq(1L)))
                .thenReturn(java.util.Optional.of(stage));
        ScenarioPackageRepository packages = mock(ScenarioPackageRepository.class);
        CombatMapViewPort mapView = mock(CombatMapViewPort.class);
        when(mapView.hasPreparedMap(adventure.id().value(), adventure.ownerPlayerId().value())).thenReturn(true);
        CombatMapPreparationPort mapPreparation = mock(CombatMapPreparationPort.class);
        RuntimeTurn turn = turnFrom("cellar", true, false);
        PendingRuntimeState pending = mock(PendingRuntimeState.class);
        when(pending.selectedSituationId()).thenReturn("missing-destination");
        when(turn.pendingState()).thenReturn(pending);

        assertThrows(IllegalStateException.class, () -> new PreparedMapEntryCommitGate(
                mapView, mapPreparation, packages, artifacts).beforeCommit(adventure, turn));

        verify(mapPreparation, never()).activatePrepared(any(), any(), any(), any(int.class), any());
        assertTrue(diagnostics.list.stream().map(ILoggingEvent::getFormattedMessage)
                .anyMatch(message -> message.contains("operation=situation_map_entry")
                        && message.contains("outcome=failed")
                        && message.contains("expectedSituationId=missing-destination")
                        && message.contains("actualSituationId=null")
                        && message.contains("recoveryAction=reject_turn")));
    }

    @Test
    void reports_expected_map_identity_when_a_map_needed_situation_has_no_usable_map() {
        Adventure adventure = adventureAt("cellar");
        UUID packageId = UUID.randomUUID();
        UUID mapId = UUID.randomUUID();
        when(adventure.lockedScenarioPackageId()).thenReturn(packageId);
        var story = mock(com.dndmaster.adventure.domain.runtime.story.StoryRuntimeState.class);
        when(adventure.storyRuntimeState()).thenReturn(story);
        when(story.activeSituationId()).thenReturn("cellar-entry");
        when(story.stageId()).thenReturn("stage-1");
        when(story.backboneRevision()).thenReturn(1L);
        when(story.detailedStageRevision()).thenReturn(1L);
        var situation = mock(SituationDefinition.class);
        when(situation.situationId()).thenReturn("cellar-entry");
        when(situation.mapNeeded()).thenReturn(true);
        when(situation.mapDefinitionId()).thenReturn(mapId);
        var stage = mock(com.dndmaster.adventure.domain.scenario.DetailedStage.class);
        when(stage.situations()).thenReturn(List.of(situation));
        StageArtifactRepository artifacts = mock(StageArtifactRepository.class);
        when(artifacts.findDetailedStage(any(), eq(packageId), eq("stage-1"), eq(1L), eq(1L)))
                .thenReturn(java.util.Optional.of(stage));
        MapDefinition unusableMap = mock(MapDefinition.class);
        when(unusableMap.id()).thenReturn(mapId);
        when(unusableMap.autoActivatable()).thenReturn(false);
        ScenarioPackage scenarioPackage = mock(ScenarioPackage.class);
        when(scenarioPackage.mapDefinitions()).thenReturn(List.of(unusableMap));
        ScenarioPackageRepository packages = mock(ScenarioPackageRepository.class);
        when(packages.findById(packageId)).thenReturn(java.util.Optional.of(scenarioPackage));
        RuntimeTurn turn = turnFrom("cellar", false, false);
        PendingRuntimeState pending = mock(PendingRuntimeState.class);
        when(pending.selectedSituationId()).thenReturn("cellar-entry");
        when(turn.pendingState()).thenReturn(pending);

        assertThrows(IllegalStateException.class, () -> new PreparedMapEntryCommitGate(
                mock(CombatMapViewPort.class), mock(CombatMapPreparationPort.class), packages, artifacts)
                .beforeCommit(adventure, turn));

        assertTrue(diagnostics.list.stream().map(ILoggingEvent::getFormattedMessage)
                .anyMatch(message -> message.contains("operation=situation_map_entry")
                        && message.contains("outcome=failed")
                        && message.contains("situationId=cellar-entry")
                        && message.contains("expectedMapDefinitionId=" + mapId)
                        && message.contains("failureClass=usable_map_unavailable")
                        && message.contains("recoveryAction=reject_turn")));
    }

    @Test
    void 같은_지도_ID를_서술_변경과_무관하게_재사용_판단에_전달한다() {
        Adventure adventure = adventureAt("cellar");
        UUID packageId = UUID.randomUUID();
        UUID mapId = UUID.randomUUID();
        when(adventure.lockedScenarioPackageId()).thenReturn(packageId);
        var story = mock(com.dndmaster.adventure.domain.runtime.story.StoryRuntimeState.class);
        when(adventure.storyRuntimeState()).thenReturn(story);
        when(story.activeSituationId()).thenReturn("cellar-entry");
        when(story.stageId()).thenReturn("stage-1");
        when(story.backboneRevision()).thenReturn(1L);
        when(story.detailedStageRevision()).thenReturn(1L);
        var situation = mock(SituationDefinition.class);
        when(situation.situationId()).thenReturn("cellar-entry");
        when(situation.mapNeeded()).thenReturn(true);
        when(situation.mapDefinitionId()).thenReturn(mapId);
        var stage = mock(com.dndmaster.adventure.domain.scenario.DetailedStage.class);
        when(stage.situations()).thenReturn(List.of(situation));
        StageArtifactRepository artifacts = mock(StageArtifactRepository.class);
        when(artifacts.findDetailedStage(any(), eq(packageId), eq("stage-1"), eq(1L), eq(1L)))
                .thenReturn(java.util.Optional.of(stage));
        MapDefinition map = mock(MapDefinition.class);
        when(map.id()).thenReturn(mapId);
        when(map.autoActivatable()).thenReturn(true);
        ScenarioPackage scenarioPackage = mock(ScenarioPackage.class);
        when(scenarioPackage.mapDefinitions()).thenReturn(List.of(map));
        ScenarioPackageRepository packages = mock(ScenarioPackageRepository.class);
        when(packages.findById(packageId)).thenReturn(java.util.Optional.of(scenarioPackage));
        CombatMapPreparationPort mapPreparation = mock(CombatMapPreparationPort.class);
        PreparedMapEntryCommitGate gate = new PreparedMapEntryCommitGate(mock(CombatMapViewPort.class),
                mapPreparation, packages, artifacts);

        RuntimeTurn opening = turnFrom("cellar", false, false);
        when(opening.conversation()).thenReturn(List.of());
        gate.beforeCommit(adventure, opening);

        RuntimeTurn laterTurn = turnFrom("맥주 저장고의 다른 표현", false, false);
        when(laterTurn.conversation()).thenReturn(List.of(new ConversationEntry(0, "AI_GAME_MASTER", "이미 열린 장면")));
        gate.beforeCommit(adventure, laterTurn);

        verify(mapPreparation, times(2)).prepareInitial(any(), any(), any(), eq(map), eq(1), any());
        verify(mapPreparation, never()).prepareDraft(any(), any(), any(), any(), any());
    }

    @Test
    void activates_a_prepared_map_when_the_scene_changes_even_if_gm_omits_the_entry_flag() {
        Adventure adventure = adventureAt("beer-cellar");
        RuntimeTurn turn = turnFrom("opening-situation", false, false);
        CombatMapViewPort mapView = mock(CombatMapViewPort.class);
        CombatMapPreparationPort mapPreparation = mock(CombatMapPreparationPort.class);
        when(mapView.hasPreparedMap(any(), any())).thenReturn(true);

        new PreparedMapEntryCommitGate(mapView, mapPreparation).beforeCommit(adventure, turn);

        ArgumentCaptor<CombatMapPreparationPort.ActivationContext> context = ArgumentCaptor.forClass(CombatMapPreparationPort.ActivationContext.class);
        verify(mapPreparation).activatePrepared(any(), any(), any(), eq(1), context.capture());
        assertEquals("beer-cellar", context.getValue().currentScene());
        assertEquals("beer-cellar", context.getValue().location());
    }

    @Test
    void does_not_activate_a_map_for_a_scene_change_when_no_prepared_map_exists() {
        Adventure adventure = adventureAt("beer-cellar");
        RuntimeTurn turn = turnFrom("opening-situation", false, false);
        CombatMapViewPort mapView = mock(CombatMapViewPort.class);
        CombatMapPreparationPort mapPreparation = mock(CombatMapPreparationPort.class);
        when(mapView.hasPreparedMap(any(), any())).thenReturn(false);

        new PreparedMapEntryCommitGate(mapView, mapPreparation).beforeCommit(adventure, turn);

        verify(mapPreparation, never()).activatePrepared(any(), any(), any(), any(int.class), any());
    }

    private static Adventure adventureAt(String scene) {
        Adventure adventure = mock(Adventure.class);
        when(adventure.id()).thenReturn(AdventureId.generate());
        when(adventure.ownerPlayerId()).thenReturn(new OwnerPlayerId(UUID.randomUUID()));
        when(adventure.ruleSetId()).thenReturn(new RuleSetId(UUID.randomUUID()));
        when(adventure.currentSituation()).thenReturn(new CurrentSituation(UUID.randomUUID(), 2, "opening",
                "문제", "위협", "목표", null, "첫 장면"));
        when(adventure.currentContext()).thenReturn(new AdventureContext(scene, null, "해치를 연다", "도착했다"));
        when(adventure.party()).thenReturn(List.of());
        when(adventure.turnIndex()).thenReturn(0);
        return adventure;
    }

    private static RuntimeTurn turnFrom(String previousScene, boolean mapEntry, boolean combatStart) {
        RuntimeTurn turn = mock(RuntimeTurn.class);
        RuntimePlan plan = mock(RuntimePlan.class);
        when(turn.context()).thenReturn(new AdventureContext(previousScene, null, "해치를 연다", ""));
        when(turn.plan()).thenReturn(plan);
        when(turn.conversation()).thenReturn(List.of(new ConversationEntry(0, "PLAYER", "이전 행동")));
        when(turn.action()).thenReturn("해치를 열고 내려간다");
        when(turn.narration()).thenReturn("저장고 안에 도착했다.");
        when(plan.mapEntryRequested()).thenReturn(mapEntry);
        when(plan.combatStartRequested()).thenReturn(combatStart);
        when(plan.judgment()).thenReturn("저장고에 도착했다.");
        when(plan.citedEvidence()).thenReturn(List.of());
        return turn;
    }
}
