package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dndmaster.adventure.application.runtime.RuntimeBindingApplicationService;
import com.dndmaster.adventure.application.runtime.RuntimeTurnApplicationService;
import com.dndmaster.adventure.application.combat.CombatMapPreparationPort;
import com.dndmaster.adventure.application.saved.AdventureRepository;
import com.dndmaster.adventure.application.scenario.compilation.ScenarioPackageRepository;
import com.dndmaster.adventure.application.scenario.preparation.StageArtifactPreparationApplicationService;
import com.dndmaster.adventure.application.session.AdventureSessionApplicationService;
import com.dndmaster.adventure.application.session.AdventureSessionRepository;
import com.dndmaster.adventure.application.session.AdventureSessionStartCoordinator;
import com.dndmaster.adventure.application.session.AiCompanionGenerationPort;
import com.dndmaster.adventure.application.session.AiCompanionSheetCreationPort;
import com.dndmaster.adventure.application.session.CharacterSheetOwnershipPort;
import com.dndmaster.adventure.application.knowledge.SessionKnowledgeSetRepository;
import com.dndmaster.adventure.domain.adventure.Adventure;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.AdventurePartyMember;
import com.dndmaster.adventure.domain.adventure.AdventureSession;
import com.dndmaster.adventure.domain.adventure.AdventureSessionRuntimeConfiguration;
import com.dndmaster.adventure.domain.adventure.AdventureContext;
import com.dndmaster.adventure.domain.adventure.CharacterSheetId;
import com.dndmaster.adventure.domain.adventure.ControlMode;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import com.dndmaster.adventure.domain.adventure.RuleSetId;
import com.dndmaster.adventure.domain.adventure.ScenarioId;
import com.dndmaster.adventure.domain.adventure.SessionId;
import com.dndmaster.adventure.domain.scenario.ScenarioPackage;
import com.dndmaster.adventure.domain.scenario.MapDefinition;
import com.dndmaster.adventure.domain.runtime.CurrentSituation;
import com.dndmaster.adventure.domain.runtime.DisclosureState;
import com.dndmaster.adventure.domain.runtime.GameState;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AdventureSessionStageStartTest {
    @Test
    void initializes_the_real_session_start_from_the_prepared_opening_situation() {
        OwnerPlayerId owner = new OwnerPlayerId(UUID.randomUUID());
        ScenarioPackage scenarioPackage = mock(ScenarioPackage.class);
        UUID packageId = UUID.randomUUID();
        when(scenarioPackage.packageId()).thenReturn(packageId);
        when(scenarioPackage.bundleRevision()).thenReturn(1L);
        when(scenarioPackage.isReady()).thenReturn(true);
        when(scenarioPackage.scenarioModel()).thenReturn(mock(com.dndmaster.adventure.domain.scenario.ScenarioModel.class));
        AdventureSession session = AdventureSession.rehydrate(SessionId.generate(), owner, packageId, 1,
                packageId, 1, 1, List.of(new AdventurePartyMember(new CharacterSheetId(UUID.randomUUID()),
                        ControlMode.DIRECT, true, true, true, true, true, true)), configuration(packageId),
                AdventureSession.Status.DRAFT, null, null, 0);
        AdventureSessionRepository sessions = mock(AdventureSessionRepository.class);
        AdventureRepository adventures = mock(AdventureRepository.class);
        when(sessions.findById(session.id())).thenReturn(Optional.of(session));
        when(adventures.findById(org.mockito.ArgumentMatchers.any())).thenReturn(Optional.empty());
        StageArtifactPreparationApplicationService preparation = mock(StageArtifactPreparationApplicationService.class);
        var prepared = mock(StageArtifactPreparationApplicationService.Result.class);
        var opening = mock(com.dndmaster.adventure.domain.scenario.SituationDefinition.class);
        when(opening.situationId()).thenReturn("prepared-opening");
        when(prepared.openingSituation()).thenReturn(opening);
        when(preparation.prepare(packageId)).thenReturn(prepared);
        RuntimeTurnApplicationService openingRuntime = mock(RuntimeTurnApplicationService.class);
        UUID requestId = UUID.randomUUID();

        AdventureSessionApplicationService service = new AdventureSessionApplicationService(sessions,
                packageRepository(scenarioPackage), adventures, mock(RuntimeBindingApplicationService.class),
                mock(AdventureSessionStartCoordinator.class), mock(CharacterSheetOwnershipPort.class),
                mock(SessionKnowledgeSetRepository.class), mock(AiCompanionGenerationPort.class),
                mock(AiCompanionSheetCreationPort.class), (adventure, player, rules, map, stage) -> null, preparation,
                openingRuntime);

        AdventureId adventureId = AdventureId.generate();
        service.start(session.id(), owner, 0, requestId, adventureId);

        org.mockito.ArgumentCaptor<Adventure> saved = org.mockito.ArgumentCaptor.forClass(Adventure.class);
        org.mockito.Mockito.verify(adventures, org.mockito.Mockito.atLeastOnce()).save(saved.capture());
        assertEquals("prepared-opening", saved.getAllValues().getLast().currentSituation().problem());
        verify(openingRuntime).openSessionTurn(adventureId, owner, requestId);
    }

    @Test
    void activates_the_prepared_map_when_the_opening_turn_commits_a_map_bearing_scene() {
        OwnerPlayerId owner = new OwnerPlayerId(UUID.randomUUID());
        ScenarioPackage scenarioPackage = mock(ScenarioPackage.class);
        UUID packageId = UUID.randomUUID();
        when(scenarioPackage.packageId()).thenReturn(packageId);
        when(scenarioPackage.bundleRevision()).thenReturn(1L);
        when(scenarioPackage.isReady()).thenReturn(true);
        when(scenarioPackage.scenarioModel()).thenReturn(mock(com.dndmaster.adventure.domain.scenario.ScenarioModel.class));
        MapDefinition mapDefinition = mock(MapDefinition.class);
        when(scenarioPackage.initialMapDefinition("legacy opening")).thenReturn(Optional.of(mapDefinition));
        var runtimeConfiguration = configuration(packageId);
        AdventureSession session = AdventureSession.rehydrate(SessionId.generate(), owner, packageId, 1,
                packageId, 1, 1, List.of(new AdventurePartyMember(new CharacterSheetId(UUID.randomUUID()),
                        ControlMode.DIRECT, true, true, true, true, true, true)), runtimeConfiguration,
                AdventureSession.Status.DRAFT, null, null, 0);
        AdventureId adventureId = AdventureId.generate();
        Adventure committed = Adventure.beginScenarioRuntime(adventureId, session.id(), owner,
                new ScenarioId(packageId), runtimeConfiguration.ruleSetId(), packageId, 1,
                session.party(), new AdventureContext("양조장 지하 저장고", null, null, null));
        committed.initializeScenarioRuntime(owner, GameState.empty(), DisclosureState.empty(),
                new CurrentSituation(UUID.randomUUID(), 1, "맥주 저장고", "쥐를 찾는다", "거대 쥐", "저장고를 조사한다"),
                List.of(), new AdventureContext("양조장 지하 저장고", null, null, null));

        AdventureSessionRepository sessions = mock(AdventureSessionRepository.class);
        when(sessions.findById(session.id())).thenReturn(Optional.of(session));
        AdventureRepository adventures = mock(AdventureRepository.class);
        when(adventures.findById(org.mockito.ArgumentMatchers.any())).thenReturn(Optional.empty(), Optional.of(committed));
        StageArtifactPreparationApplicationService preparation = mock(StageArtifactPreparationApplicationService.class);
        var prepared = mock(StageArtifactPreparationApplicationService.Result.class);
        var opening = mock(com.dndmaster.adventure.domain.scenario.SituationDefinition.class);
        when(opening.situationId()).thenReturn("prepared-opening");
        when(prepared.openingSituation()).thenReturn(opening);
        when(preparation.prepare(packageId)).thenReturn(prepared);
        RuntimeTurnApplicationService openingRuntime = mock(RuntimeTurnApplicationService.class);
        UUID requestId = UUID.randomUUID();
        var openingResult = mock(com.dndmaster.adventure.application.runtime.RuntimeTurnResult.class);
        var openingTurn = mock(com.dndmaster.adventure.application.runtime.RuntimeTurn.class);
        var openingPlan = mock(com.dndmaster.adventure.application.runtime.RuntimePlan.class);
        when(openingRuntime.openSessionTurn(adventureId, owner, requestId)).thenReturn(openingResult);
        when(openingResult.turn()).thenReturn(openingTurn);
        when(openingTurn.plan()).thenReturn(openingPlan);
        when(openingPlan.mapEntryRequested()).thenReturn(true);
        CombatMapPreparationPort maps = mock(CombatMapPreparationPort.class);
        when(maps.mapLayoutConfirmed(adventureId, owner.value())).thenReturn(true);

        AdventureSessionApplicationService service = new AdventureSessionApplicationService(sessions,
                packageRepository(scenarioPackage), adventures, mock(RuntimeBindingApplicationService.class),
                mock(AdventureSessionStartCoordinator.class), mock(CharacterSheetOwnershipPort.class),
                mock(SessionKnowledgeSetRepository.class), mock(AiCompanionGenerationPort.class),
                mock(AiCompanionSheetCreationPort.class), maps, preparation, openingRuntime);

        service.start(session.id(), owner, 0, requestId, adventureId);

        verify(maps).activatePrepared(org.mockito.ArgumentMatchers.eq(adventureId), org.mockito.ArgumentMatchers.eq(owner.value()),
                org.mockito.ArgumentMatchers.eq(runtimeConfiguration.ruleSetId()), org.mockito.ArgumentMatchers.eq(1),
                org.mockito.ArgumentMatchers.any(CombatMapPreparationPort.ActivationContext.class));
    }

    @Test
    void leaves_the_session_draft_when_stage_preparation_fails() {
        OwnerPlayerId owner = new OwnerPlayerId(UUID.randomUUID());
        ScenarioPackage scenarioPackage = mock(ScenarioPackage.class);
        UUID packageId = UUID.randomUUID();
        when(scenarioPackage.packageId()).thenReturn(packageId);
        when(scenarioPackage.bundleRevision()).thenReturn(1L);
        when(scenarioPackage.isReady()).thenReturn(true);
        when(scenarioPackage.scenarioModel()).thenReturn(mock(com.dndmaster.adventure.domain.scenario.ScenarioModel.class));
        AdventureSession session = AdventureSession.rehydrate(SessionId.generate(), owner, packageId, 1,
                packageId, 1, 1, List.of(new AdventurePartyMember(new CharacterSheetId(UUID.randomUUID()),
                        ControlMode.DIRECT, true, true, true, true, true, true)), configuration(packageId),
                AdventureSession.Status.DRAFT, null, null, 0);
        AdventureSessionRepository sessions = mock(AdventureSessionRepository.class);
        when(sessions.findById(session.id())).thenReturn(Optional.of(session));
        StageArtifactPreparationApplicationService preparation = mock(StageArtifactPreparationApplicationService.class);
        when(preparation.prepare(packageId)).thenThrow(new IllegalStateException("published Storybook evidence is required"));
        AdventureSessionApplicationService service = new AdventureSessionApplicationService(sessions,
                packageRepository(scenarioPackage), mock(AdventureRepository.class), mock(RuntimeBindingApplicationService.class),
                mock(AdventureSessionStartCoordinator.class), mock(CharacterSheetOwnershipPort.class),
                mock(SessionKnowledgeSetRepository.class), mock(AiCompanionGenerationPort.class),
                mock(AiCompanionSheetCreationPort.class), (adventure, player, rules, map, stage) -> null, preparation);

        assertThrows(IllegalStateException.class,
                () -> service.start(session.id(), owner, 0, UUID.randomUUID(), AdventureId.generate()));

        assertEquals(AdventureSession.Status.DRAFT, session.status());
        verify(sessions, never()).save(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void reuses_adventure_already_persisted_for_session_when_retry_uses_new_id() {
        OwnerPlayerId owner = new OwnerPlayerId(UUID.randomUUID());
        ScenarioPackage scenarioPackage = mock(ScenarioPackage.class);
        UUID packageId = UUID.randomUUID();
        when(scenarioPackage.packageId()).thenReturn(packageId);
        when(scenarioPackage.bundleRevision()).thenReturn(1L);
        when(scenarioPackage.isReady()).thenReturn(true);
        when(scenarioPackage.scenarioModel()).thenReturn(mock(com.dndmaster.adventure.domain.scenario.ScenarioModel.class));
        AdventureSession session = AdventureSession.rehydrate(SessionId.generate(), owner, packageId, 1,
                packageId, 1, 1, List.of(new AdventurePartyMember(new CharacterSheetId(UUID.randomUUID()),
                        ControlMode.DIRECT, true, true, true, true, true, true)), configuration(packageId),
                AdventureSession.Status.DRAFT, null, null, 0);
        AdventureId persistedId = AdventureId.generate();
        Adventure persisted = Adventure.beginScenarioRuntime(persistedId, session.id(), owner,
                new ScenarioId(packageId), new RuleSetId(UUID.randomUUID()), packageId, 1,
                session.party(), new com.dndmaster.adventure.domain.adventure.AdventureContext("opening", null, null, null));
        AdventureSessionRepository sessions = mock(AdventureSessionRepository.class);
        AdventureRepository adventures = mock(AdventureRepository.class);
        when(sessions.findById(session.id())).thenReturn(Optional.of(session));
        when(adventures.findById(org.mockito.ArgumentMatchers.any())).thenReturn(Optional.empty());
        when(adventures.findBySessionId(session.id())).thenReturn(Optional.of(persisted));
        StageArtifactPreparationApplicationService preparation = mock(StageArtifactPreparationApplicationService.class);
        var prepared = mock(StageArtifactPreparationApplicationService.Result.class);
        var opening = mock(com.dndmaster.adventure.domain.scenario.SituationDefinition.class);
        when(opening.situationId()).thenReturn("prepared-opening");
        when(prepared.openingSituation()).thenReturn(opening);
        when(preparation.prepare(packageId)).thenReturn(prepared);
        AdventureSessionStartCoordinator coordinator = mock(AdventureSessionStartCoordinator.class);

        AdventureSessionApplicationService service = new AdventureSessionApplicationService(sessions,
                packageRepository(scenarioPackage), adventures, mock(RuntimeBindingApplicationService.class),
                coordinator, mock(CharacterSheetOwnershipPort.class), mock(SessionKnowledgeSetRepository.class),
                mock(AiCompanionGenerationPort.class), mock(AiCompanionSheetCreationPort.class),
                (adventure, player, rules, map, stage) -> null, preparation);

        service.start(session.id(), owner, 0, UUID.randomUUID(), AdventureId.generate());

        org.mockito.Mockito.verify(coordinator).prepare(org.mockito.ArgumentMatchers.eq(session.id()), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(persistedId.value()), org.mockito.ArgumentMatchers.eq(packageId));
    }

    private static AdventureSessionRuntimeConfiguration configuration(UUID packageId) {
        return new AdventureSessionRuntimeConfiguration(new ScenarioId(packageId), new RuleSetId(UUID.randomUUID()),
                List.of(), "engine", List.of(), "legacy opening");
    }

    private static ScenarioPackageRepository packageRepository(ScenarioPackage scenarioPackage) {
        return new ScenarioPackageRepository() {
            @Override public Optional<ScenarioPackage> findById(UUID id) { return Optional.of(scenarioPackage); }
            @Override public Optional<ScenarioPackage> findByInputFingerprint(String fingerprint) { return Optional.empty(); }
            @Override public List<ScenarioPackage> findByBundleId(UUID id) { return List.of(); }
            @Override public void save(ScenarioPackage value) {}
        };
    }
}
