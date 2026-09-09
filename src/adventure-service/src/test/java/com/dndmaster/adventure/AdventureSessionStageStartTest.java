package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dndmaster.adventure.application.runtime.RuntimeBindingApplicationService;
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
import com.dndmaster.adventure.domain.adventure.CharacterSheetId;
import com.dndmaster.adventure.domain.adventure.ControlMode;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import com.dndmaster.adventure.domain.adventure.RuleSetId;
import com.dndmaster.adventure.domain.adventure.ScenarioId;
import com.dndmaster.adventure.domain.adventure.SessionId;
import com.dndmaster.adventure.domain.scenario.ScenarioPackage;
import com.dndmaster.adventure.domain.scenario.MapDefinition;
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

        AdventureSessionApplicationService service = new AdventureSessionApplicationService(sessions,
                packageRepository(scenarioPackage), adventures, mock(RuntimeBindingApplicationService.class),
                mock(AdventureSessionStartCoordinator.class), mock(CharacterSheetOwnershipPort.class),
                mock(SessionKnowledgeSetRepository.class), mock(AiCompanionGenerationPort.class),
                mock(AiCompanionSheetCreationPort.class), (adventure, player, rules, map, stage) -> null, preparation);

        service.start(session.id(), owner, 0, UUID.randomUUID(), AdventureId.generate());

        org.mockito.ArgumentCaptor<Adventure> saved = org.mockito.ArgumentCaptor.forClass(Adventure.class);
        org.mockito.Mockito.verify(adventures, org.mockito.Mockito.atLeastOnce()).save(saved.capture());
        assertEquals("prepared-opening", saved.getAllValues().getLast().currentSituation().problem());
    }

    @Test
    void map_preparation_keeps_session_starting_until_player_confirms() {
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
        RuntimeBindingApplicationService bindings = mock(RuntimeBindingApplicationService.class);

        AdventureSessionApplicationService service = new AdventureSessionApplicationService(sessions,
                packageRepository(scenarioPackage), adventures, bindings,
                mock(AdventureSessionStartCoordinator.class), mock(CharacterSheetOwnershipPort.class),
                mock(SessionKnowledgeSetRepository.class), mock(AiCompanionGenerationPort.class),
                mock(AiCompanionSheetCreationPort.class), (adventure, player, rules, map, stage) -> null, preparation);

        AdventureSession preparedSession = service.start(session.id(), owner, 0, UUID.randomUUID(), AdventureId.generate(), true);

        assertEquals(AdventureSession.Status.STARTING, preparedSession.status());
        verify(bindings, never()).bindForSession(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void final_start_keeps_the_map_as_a_draft_until_combat_enters() {
        OwnerPlayerId owner = new OwnerPlayerId(UUID.randomUUID());
        ScenarioPackage scenarioPackage = mock(ScenarioPackage.class);
        UUID packageId = UUID.randomUUID();
        when(scenarioPackage.packageId()).thenReturn(packageId);
        when(scenarioPackage.bundleRevision()).thenReturn(1L);
        when(scenarioPackage.isReady()).thenReturn(true);
        when(scenarioPackage.scenarioModel()).thenReturn(mock(com.dndmaster.adventure.domain.scenario.ScenarioModel.class));
        MapDefinition mapDefinition = mock(MapDefinition.class);
        when(scenarioPackage.initialMapDefinition(org.mockito.ArgumentMatchers.anyString())).thenReturn(Optional.of(mapDefinition));
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
        RuntimeBindingApplicationService bindings = mock(RuntimeBindingApplicationService.class);
        CombatMapPreparationPort maps = mock(CombatMapPreparationPort.class);
        when(maps.mapLayoutConfirmed(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(true);

        AdventureSessionApplicationService service = new AdventureSessionApplicationService(sessions,
                packageRepository(scenarioPackage), adventures, bindings,
                mock(AdventureSessionStartCoordinator.class), mock(CharacterSheetOwnershipPort.class),
                mock(SessionKnowledgeSetRepository.class), mock(AiCompanionGenerationPort.class),
                mock(AiCompanionSheetCreationPort.class), maps, preparation);

        service.start(session.id(), owner, 0, UUID.randomUUID(), AdventureId.generate());

        verify(maps).prepareDraft(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(owner.value()),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.same(mapDefinition), org.mockito.ArgumentMatchers.any());
        verify(maps, never()).prepareInitial(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void resumes_a_pending_start_after_the_browser_issues_a_new_idempotency_key() {
        OwnerPlayerId owner = new OwnerPlayerId(UUID.randomUUID());
        ScenarioPackage scenarioPackage = mock(ScenarioPackage.class);
        UUID packageId = UUID.randomUUID();
        when(scenarioPackage.packageId()).thenReturn(packageId);
        when(scenarioPackage.bundleRevision()).thenReturn(1L);
        when(scenarioPackage.isReady()).thenReturn(true);
        when(scenarioPackage.scenarioModel()).thenReturn(mock(com.dndmaster.adventure.domain.scenario.ScenarioModel.class));
        AdventureId persistedId = AdventureId.generate();
        AdventureSession session = AdventureSession.rehydrate(SessionId.generate(), owner, packageId, 1,
                packageId, 1, 1, List.of(new AdventurePartyMember(new CharacterSheetId(UUID.randomUUID()),
                        ControlMode.DIRECT, true, true, true, true, true, true)), configuration(packageId),
                AdventureSession.Status.STARTING, persistedId, UUID.randomUUID(), 1);
        Adventure persisted = Adventure.beginScenarioRuntime(persistedId, session.id(), owner,
                new ScenarioId(packageId), new RuleSetId(UUID.randomUUID()), packageId, 1,
                session.party(), new com.dndmaster.adventure.domain.adventure.AdventureContext("opening", null, null, null));
        AdventureSessionRepository sessions = mock(AdventureSessionRepository.class);
        AdventureRepository adventures = mock(AdventureRepository.class);
        when(sessions.findById(session.id())).thenReturn(Optional.of(session));
        when(adventures.findById(org.mockito.ArgumentMatchers.any())).thenReturn(Optional.of(persisted));
        StageArtifactPreparationApplicationService preparation = mock(StageArtifactPreparationApplicationService.class);
        var prepared = mock(StageArtifactPreparationApplicationService.Result.class);
        var opening = mock(com.dndmaster.adventure.domain.scenario.SituationDefinition.class);
        when(opening.situationId()).thenReturn("prepared-opening");
        when(prepared.openingSituation()).thenReturn(opening);
        when(preparation.prepare(packageId)).thenReturn(prepared);
        RuntimeBindingApplicationService bindings = mock(RuntimeBindingApplicationService.class);
        var maps = new com.dndmaster.adventure.application.combat.CombatMapPreparationPort() {
            @Override public UUID prepareInitial(AdventureId adventureId, UUID ownerPlayerId, RuleSetId ruleSetId,
                    com.dndmaster.adventure.domain.scenario.MapDefinition mapDefinition, int stagePosition) { return null; }
            @Override public boolean mapLayoutConfirmed(AdventureId adventureId, UUID ownerPlayerId) { return true; }
        };

        AdventureSessionApplicationService service = new AdventureSessionApplicationService(sessions,
                packageRepository(scenarioPackage), adventures, bindings,
                mock(AdventureSessionStartCoordinator.class), mock(CharacterSheetOwnershipPort.class),
                mock(SessionKnowledgeSetRepository.class), mock(AiCompanionGenerationPort.class),
                mock(AiCompanionSheetCreationPort.class), maps, preparation);

        AdventureSession resumed = service.start(session.id(), owner, 999, UUID.randomUUID(), AdventureId.generate());

        assertEquals(AdventureSession.Status.STARTED, resumed.status());
        verify(bindings).bindForSession(org.mockito.ArgumentMatchers.any());
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
