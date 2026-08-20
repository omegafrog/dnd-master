package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndmaster.adventure.domain.adventure.AdventurePartyMember;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.AdventureSessionRuntimeConfiguration;
import com.dndmaster.adventure.domain.adventure.AdventureSession;
import com.dndmaster.adventure.domain.adventure.CharacterSheetId;
import com.dndmaster.adventure.domain.adventure.ControlMode;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import com.dndmaster.adventure.domain.adventure.SessionId;
import com.dndmaster.adventure.domain.adventure.ScenarioId;
import com.dndmaster.adventure.domain.adventure.RuleSetId;
import com.dndmaster.adventure.application.runtime.RuntimeBindingApplicationService;
import com.dndmaster.adventure.application.saved.AdventureRepository;
import com.dndmaster.adventure.application.scenario.compilation.ScenarioPackageRepository;
import com.dndmaster.adventure.application.session.AdventureSessionApplicationService;
import com.dndmaster.adventure.application.session.AdventureSessionRepository;
import com.dndmaster.adventure.application.session.AdventureSessionStartCoordinator;
import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import com.dndmaster.adventure.domain.knowledge.SessionKnowledgeSet;
import com.dndmaster.adventure.application.knowledge.SessionKnowledgeSetRepository;
import com.dndmaster.adventure.domain.scenario.CharacterLimit;
import com.dndmaster.adventure.domain.scenario.ResolutionStatus;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleDocumentRole;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleDocumentSelection;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleId;
import com.dndmaster.adventure.domain.scenario.ScenarioCompilationReport;
import com.dndmaster.adventure.domain.scenario.ScenarioPackage;
import com.dndmaster.adventure.application.knowledge.KnowledgeDocumentStatus;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AdventureSessionTest {
    @Test
    void retains_runtime_configuration_required_to_start_a_draft() {
        ScenarioId scenarioId = new ScenarioId(UUID.randomUUID());
        RuleSetId ruleSetId = new RuleSetId(UUID.randomUUID());
        AdventureSessionRuntimeConfiguration configuration = new AdventureSessionRuntimeConfiguration(
                scenarioId, ruleSetId, List.of(UUID.randomUUID()), "ollama", List.of("search", "move"), "opening-scene");

        AdventureSession session = AdventureSession.create(
                SessionId.generate(), new OwnerPlayerId(UUID.randomUUID()), UUID.randomUUID(), 1, 1, configuration);

        assertEquals(configuration, session.runtimeConfiguration());
    }

    @Test
    void retains_the_character_edition_bound_when_the_session_was_created() {
        AdventureSession session = AdventureSession.create(
                SessionId.generate(), new OwnerPlayerId(UUID.randomUUID()), UUID.randomUUID(), 1,
                UUID.randomUUID(), 2, "DND_5E_2024", 1,
                new AdventureSessionRuntimeConfiguration(new ScenarioId(UUID.randomUUID()), new RuleSetId(UUID.randomUUID()),
                        List.of(), "ollama", List.of(), "opening-scene"));

        assertEquals("DND_5E_2024", session.characterEdition());
    }

    @Test
    void creates_default_runtime_configuration_from_compiled_package() {
        var packages = mock(ScenarioPackageRepository.class);
        var bundleId = ScenarioBundleId.generate();
        var rulebookId = UUID.randomUUID();
        var scenarioPackage = ScenarioPackage.publish(bundleId, 1, "fingerprint", List.of(
                new ScenarioBundleDocumentSelection(new KnowledgeDocumentId(rulebookId), ScenarioBundleDocumentRole.RULEBOOK,
                        KnowledgeDocumentStatus.INDEXED, "rules.pdf", "RULEBOOK", 1)), List.of(),
                new ScenarioCompilationReport(ResolutionStatus.COMPLETE, List.of()), CharacterLimit.defaultLimit(),
                new com.dndmaster.adventure.domain.scenario.CharacterCreationBlueprint(1,
                        com.dndmaster.adventure.domain.scenario.CharacterCreationBlueprintStatus.PUBLISHED,
                        List.of(new com.dndmaster.adventure.domain.scenario.CharacterCreationBlueprint.Field(
                                "race", List.of("Elf"), true, "RULEBOOK", List.of(), "EXTRACTED", List.of())), List.of()));
        when(packages.findById(scenarioPackage.packageId())).thenReturn(java.util.Optional.of(scenarioPackage));
        var sessions = mock(AdventureSessionRepository.class);
        var service = new AdventureSessionApplicationService(sessions, packages, mock(AdventureRepository.class),
                mock(RuntimeBindingApplicationService.class), mock(AdventureSessionStartCoordinator.class));

        var session = service.create(new OwnerPlayerId(UUID.randomUUID()), scenarioPackage.packageId(), (AdventureSessionRuntimeConfiguration) null);

        assertEquals(scenarioPackage.packageId(), session.runtimeConfiguration().scenarioId().value());
        assertEquals(bundleId.value(), session.runtimeConfiguration().ruleSetId().value());
        assertEquals(List.of(rulebookId), session.runtimeConfiguration().rulebookIds());
        assertEquals("opening", session.runtimeConfiguration().initialScene());
    }

    @Test
    void rejects_session_creation_from_an_unpublished_blueprint_revision() {
        var packages = mock(ScenarioPackageRepository.class);
        var scenarioPackage = ScenarioPackage.publish(ScenarioBundleId.generate(), 1, "fingerprint", List.of(), List.of(),
                new ScenarioCompilationReport(ResolutionStatus.COMPLETE, List.of()), CharacterLimit.defaultLimit(),
                new com.dndmaster.adventure.domain.scenario.CharacterCreationBlueprint(4,
                        com.dndmaster.adventure.domain.scenario.CharacterCreationBlueprintStatus.NEEDS_REVIEW,
                        List.of(new com.dndmaster.adventure.domain.scenario.CharacterCreationBlueprint.Field(
                                "race", List.of("Elf"), true, "RULEBOOK", List.of(), "EXTRACTED", List.of())), List.of()));
        when(packages.findById(scenarioPackage.packageId())).thenReturn(java.util.Optional.of(scenarioPackage));
        var service = new AdventureSessionApplicationService(mock(AdventureSessionRepository.class), packages,
                mock(AdventureRepository.class), mock(RuntimeBindingApplicationService.class),
                mock(AdventureSessionStartCoordinator.class));

        assertThrows(IllegalStateException.class, () -> service.create(new OwnerPlayerId(UUID.randomUUID()),
                scenarioPackage.packageId(), scenarioPackage.packageId(), 4, null));
    }

    @Test
    void initializes_session_knowledge_scope_from_package_documents() {
        var packages = mock(ScenarioPackageRepository.class);
        var documentId = new KnowledgeDocumentId(UUID.randomUUID());
        var owner = new OwnerPlayerId(UUID.randomUUID());
        var scenarioPackage = ScenarioPackage.publish(ScenarioBundleId.generate(), 1, "fingerprint", List.of(
                new ScenarioBundleDocumentSelection(documentId, ScenarioBundleDocumentRole.MAIN_SCENARIO,
                        KnowledgeDocumentStatus.INDEXED, "story.pdf", "STORYBOOK", 1)), List.of(),
                new ScenarioCompilationReport(ResolutionStatus.COMPLETE, List.of()), CharacterLimit.defaultLimit(),
                new com.dndmaster.adventure.domain.scenario.CharacterCreationBlueprint(1,
                        com.dndmaster.adventure.domain.scenario.CharacterCreationBlueprintStatus.PUBLISHED,
                        List.of(), List.of()));
        when(packages.findById(scenarioPackage.packageId())).thenReturn(java.util.Optional.of(scenarioPackage));
        var session = AdventureSession.create(SessionId.generate(), owner, scenarioPackage.packageId(), 1,
                scenarioPackage.packageId(), 1, 1,
                new AdventureSessionRuntimeConfiguration(new ScenarioId(scenarioPackage.packageId()),
                        new RuleSetId(scenarioPackage.bundleId().value()), List.of(), "ollama", List.of(), "opening"));
        session.addPartyMember(new AdventurePartyMember(new CharacterSheetId(UUID.randomUUID()), ControlMode.DIRECT,
                true, true, true, true, true, true));
        var sessions = mock(AdventureSessionRepository.class);
        when(sessions.findById(session.id())).thenReturn(java.util.Optional.of(session));
        var adventures = mock(AdventureRepository.class);
        when(adventures.findById(any())).thenReturn(java.util.Optional.empty());
        var scopes = mock(SessionKnowledgeSetRepository.class);
        var plans = mock(com.dndmaster.adventure.application.storyplan.AdventureStoryPlanRepository.class);
        when(plans.findBySessionId(session.id())).thenReturn(java.util.Optional.of(
                com.dndmaster.adventure.domain.adventure.AdventureStoryPlan.ready(session.id(), session.version(), 1,
                        List.of(new com.dndmaster.adventure.domain.adventure.AdventureStoryPlanStage(
                                1, "Opening", "Start", "Threat", "Resolve", List.of(), List.of())))));
        new AdventureSessionApplicationService(
                sessions, packages, adventures,
                mock(RuntimeBindingApplicationService.class), mock(AdventureSessionStartCoordinator.class),
                mock(com.dndmaster.adventure.application.session.CharacterSheetOwnershipPort.class),
                plans, scopes)
                .start(session.id(), owner, session.version(), UUID.randomUUID(), AdventureId.generate());

        verify(scopes).save(new SessionKnowledgeSet(session.id(), List.of(documentId)));
    }

    @Test
    void starts_once_and_freezes_party() {
        AdventureSession session = configuredSession();
        AdventurePartyMember member = new AdventurePartyMember(new CharacterSheetId(UUID.randomUUID()), ControlMode.DIRECT, true, true, true, true, true, true);
        session.addPartyMember(member);
        session.addPartyMember(new AdventurePartyMember(new CharacterSheetId(UUID.randomUUID()), ControlMode.AGENT, true, true, true, true, true, true));
        UUID requestId = UUID.randomUUID();
        AdventureId adventureId = AdventureId.generate();

        session.start(adventureId, requestId);

        assertEquals(AdventureSession.Status.STARTED, session.status());
        assertEquals(adventureId, session.startedAdventureId());
        assertEquals(session, session.start(adventureId, requestId));
        assertThrows(IllegalStateException.class, () -> session.addPartyMember(new AdventurePartyMember(
                new CharacterSheetId(UUID.randomUUID()), ControlMode.AGENT, true, true, true, true, true, true)));
        assertThrows(IllegalStateException.class, () -> session.replacePartyMember(new AdventurePartyMember(
                member.characterSheetId(), ControlMode.AGENT, false, false, false, false, false, false)));
        assertThrows(IllegalStateException.class, () -> session.removePartyMember(member.characterSheetId()));
    }

    @Test
    void refuses_to_start_when_story_plan_is_missing_without_mutating_session_or_runtime() {
        var owner = new OwnerPlayerId(UUID.randomUUID());
        var packageId = ScenarioBundleId.generate();
        var blueprint = new com.dndmaster.adventure.domain.scenario.CharacterCreationBlueprint(
                1, com.dndmaster.adventure.domain.scenario.CharacterCreationBlueprintStatus.PUBLISHED,
                List.of(), List.of());
        var scenarioPackage = ScenarioPackage.publish(packageId, 1, "fingerprint",
                List.of(), List.of(), new ScenarioCompilationReport(ResolutionStatus.COMPLETE, List.of()),
                new CharacterLimit(1, null, ""), blueprint);
        var packages = mock(ScenarioPackageRepository.class);
        when(packages.findById(scenarioPackage.packageId())).thenReturn(java.util.Optional.of(scenarioPackage));
        var session = AdventureSession.create(SessionId.generate(), owner, scenarioPackage.packageId(), 1,
                scenarioPackage.packageId(), 1, 1,
                new AdventureSessionRuntimeConfiguration(new ScenarioId(scenarioPackage.packageId()),
                        new RuleSetId(packageId.value()), List.of(), "ollama", List.of(), "opening"));
        session.addPartyMember(new AdventurePartyMember(new CharacterSheetId(UUID.randomUUID()), ControlMode.DIRECT,
                true, true, true, true, true, true));

        var sessions = mock(AdventureSessionRepository.class);
        when(sessions.findById(session.id())).thenReturn(java.util.Optional.of(session));
        var adventures = mock(AdventureRepository.class);
        var coordinator = mock(AdventureSessionStartCoordinator.class);
        var plans = mock(com.dndmaster.adventure.application.storyplan.AdventureStoryPlanRepository.class);
        when(plans.findBySessionId(session.id())).thenReturn(java.util.Optional.empty());
        var service = new AdventureSessionApplicationService(sessions, packages, adventures,
                mock(RuntimeBindingApplicationService.class), coordinator,
                mock(com.dndmaster.adventure.application.session.CharacterSheetOwnershipPort.class), plans,
                mock(SessionKnowledgeSetRepository.class));

        var failure = assertThrows(IllegalStateException.class,
                () -> service.start(session.id(), owner, session.version(), UUID.randomUUID(), AdventureId.generate()));

        assertEquals("adventure story plan is required", failure.getMessage());
        assertEquals(AdventureSession.Status.DRAFT, session.status());
        verify(sessions, never()).save(any(), anyLong());
        verify(adventures, never()).save(any());
        verify(coordinator, never()).prepare(any(), any(), any(), any());
    }

    @Test
    void refuses_to_start_when_tactical_scene_generation_blocked_the_story_plan() {
        var owner = new OwnerPlayerId(UUID.randomUUID());
        var packageId = ScenarioBundleId.generate();
        var blueprint = new com.dndmaster.adventure.domain.scenario.CharacterCreationBlueprint(
                1, com.dndmaster.adventure.domain.scenario.CharacterCreationBlueprintStatus.PUBLISHED, List.of(), List.of());
        var scenarioPackage = ScenarioPackage.publish(packageId, 1, "fingerprint", List.of(), List.of(),
                new ScenarioCompilationReport(ResolutionStatus.COMPLETE, List.of()), new CharacterLimit(1, null, ""), blueprint);
        var session = AdventureSession.create(SessionId.generate(), owner, scenarioPackage.packageId(), 1,
                scenarioPackage.packageId(), 1, 1,
                new AdventureSessionRuntimeConfiguration(new ScenarioId(scenarioPackage.packageId()),
                        new RuleSetId(packageId.value()), List.of(), "ollama", List.of(), "opening"));
        session.addPartyMember(new AdventurePartyMember(new CharacterSheetId(UUID.randomUUID()), ControlMode.DIRECT,
                true, true, true, true, true, true));
        var packages = mock(ScenarioPackageRepository.class);
        when(packages.findById(scenarioPackage.packageId())).thenReturn(java.util.Optional.of(scenarioPackage));
        var sessions = mock(AdventureSessionRepository.class);
        when(sessions.findById(session.id())).thenReturn(java.util.Optional.of(session));
        var plans = mock(com.dndmaster.adventure.application.storyplan.AdventureStoryPlanRepository.class);
        when(plans.findBySessionId(session.id())).thenReturn(java.util.Optional.of(
                com.dndmaster.adventure.domain.adventure.AdventureStoryPlan.blocked(UUID.randomUUID(), session.id(), 1, session.version(), 1,
                        com.dndmaster.adventure.domain.adventure.AdventurePlanConfiguration.defaults(), List.of(), "tactical validation failed")));
        var coordinator = mock(AdventureSessionStartCoordinator.class);
        var service = new AdventureSessionApplicationService(sessions, packages, mock(AdventureRepository.class),
                mock(RuntimeBindingApplicationService.class), coordinator,
                mock(com.dndmaster.adventure.application.session.CharacterSheetOwnershipPort.class), plans,
                mock(SessionKnowledgeSetRepository.class));

        var failure = assertThrows(IllegalStateException.class,
                () -> service.start(session.id(), owner, session.version(), UUID.randomUUID(), AdventureId.generate()));

        assertEquals("adventure story plan is not ready for current party", failure.getMessage());
        verify(coordinator, never()).prepare(any(), any(), any(), any());
    }

    @Test
    void records_starting_before_external_runtime_creation_and_completes_once() {
        AdventureSession session = configuredSession();
        session.addPartyMember(new AdventurePartyMember(new CharacterSheetId(UUID.randomUUID()), ControlMode.DIRECT, true, true, true, true, true, true));
        session.addPartyMember(new AdventurePartyMember(new CharacterSheetId(UUID.randomUUID()), ControlMode.AGENT, true, true, true, true, true, true));
        AdventureId adventureId = AdventureId.generate();
        UUID requestId = UUID.randomUUID();

        assertEquals(true, session.beginStart(adventureId, requestId));
        assertEquals(AdventureSession.Status.STARTING, session.status());
        assertEquals(false, session.beginStart(adventureId, requestId));
        session.completeStart();
        assertEquals(AdventureSession.Status.STARTED, session.status());
        assertEquals(session, session.start(adventureId, requestId));
    }

    private static AdventureSession configuredSession() {
        return AdventureSession.create(SessionId.generate(), new OwnerPlayerId(UUID.randomUUID()), UUID.randomUUID(), 1, 2,
                new AdventureSessionRuntimeConfiguration(new ScenarioId(UUID.randomUUID()), new RuleSetId(UUID.randomUUID()), List.of(), "ollama", List.of("search"), "opening"));
    }

    @Test
    void counts_ai_companions_toward_total_storybook_party_capacity() {
        AdventureSession session = AdventureSession.create(
                SessionId.generate(), new OwnerPlayerId(UUID.randomUUID()), UUID.randomUUID(), 1, 1);
        AdventurePartyMember first = new AdventurePartyMember(
                new CharacterSheetId(UUID.randomUUID()), ControlMode.DIRECT, true, false, true, false, true, false);

        session.addPartyMember(first);

        assertEquals(1, session.party().size());
        assertEquals(1, session.party().size());
        assertThrows(IllegalStateException.class, () -> session.addPartyMember(new AdventurePartyMember(
                new CharacterSheetId(UUID.randomUUID()), ControlMode.AGENT, true, true, true, true, true, true)));
    }

    @Test
    void rejects_start_validation_before_party_is_read() {
        AdventureSession session = configuredSession();
        assertThrows(IllegalStateException.class, session::validateStart);
    }

    @Test
    void completed_or_deleted_session_is_terminal_and_exposes_sheet_cleanup_ids() {
        AdventureSession session = configuredSession();
        CharacterSheetId sheetId = new CharacterSheetId(UUID.randomUUID());
        session.addPartyMember(new AdventurePartyMember(sheetId, ControlMode.DIRECT, true, true, true, true, true, true));
        session.addPartyMember(new AdventurePartyMember(new CharacterSheetId(UUID.randomUUID()), ControlMode.AGENT, true, true, true, true, true, true));
        session.start(AdventureId.generate(), UUID.randomUUID());

        assertTrue(session.complete().contains(sheetId));
        assertEquals(AdventureSession.Status.COMPLETED, session.status());
        assertThrows(IllegalStateException.class, () -> session.addPartyMember(
                new AdventurePartyMember(new CharacterSheetId(UUID.randomUUID()), ControlMode.DIRECT, true, true, true, true, true, true)));

        AdventureSession deleted = configuredSession();
        deleted.addPartyMember(new AdventurePartyMember(sheetId, ControlMode.DIRECT, true, true, true, true, true, true));
        deleted.addPartyMember(new AdventurePartyMember(new CharacterSheetId(UUID.randomUUID()), ControlMode.AGENT, true, true, true, true, true, true));
        deleted.start(AdventureId.generate(), UUID.randomUUID());
        assertTrue(deleted.delete().contains(sheetId));
        assertEquals(AdventureSession.Status.DELETED, deleted.status());
    }
}
