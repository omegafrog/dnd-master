package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void creates_default_runtime_configuration_from_compiled_package() {
        var packages = mock(ScenarioPackageRepository.class);
        var bundleId = ScenarioBundleId.generate();
        var rulebookId = UUID.randomUUID();
        var scenarioPackage = ScenarioPackage.publish(bundleId, 1, "fingerprint", List.of(
                new ScenarioBundleDocumentSelection(new KnowledgeDocumentId(rulebookId), ScenarioBundleDocumentRole.RULEBOOK,
                        KnowledgeDocumentStatus.INDEXED, "rules.pdf", "RULEBOOK", 1)), List.of(),
                new ScenarioCompilationReport(ResolutionStatus.COMPLETE, List.of()), CharacterLimit.defaultLimit());
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
    void starts_once_and_freezes_party() {
        AdventureSession session = configuredSession();
        AdventurePartyMember member = new AdventurePartyMember(new CharacterSheetId(UUID.randomUUID()), ControlMode.DIRECT, true, true, true, true, true, true);
        session.addPartyMember(member);
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
    void records_starting_before_external_runtime_creation_and_completes_once() {
        AdventureSession session = configuredSession();
        session.addPartyMember(new AdventurePartyMember(new CharacterSheetId(UUID.randomUUID()), ControlMode.DIRECT, true, true, true, true, true, true));
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
    void permits_ai_companion_beyond_single_player_storybook_limit() {
        AdventureSession session = AdventureSession.create(
                SessionId.generate(), new OwnerPlayerId(UUID.randomUUID()), UUID.randomUUID(), 1, 1);
        AdventurePartyMember first = new AdventurePartyMember(
                new CharacterSheetId(UUID.randomUUID()), ControlMode.DIRECT, true, false, true, false, true, false);

        session.addPartyMember(first);

        assertEquals(1, session.party().size());
        session.addPartyMember(new AdventurePartyMember(
                new CharacterSheetId(UUID.randomUUID()), ControlMode.AGENT, true, true, true, true, true, true));
        assertEquals(2, session.party().size());
        assertThrows(IllegalStateException.class, () -> session.addPartyMember(new AdventurePartyMember(
                new CharacterSheetId(UUID.randomUUID()), ControlMode.DIRECT, true, true, true, true, true, true)));
        session.removePartyMember(first.characterSheetId());
        assertEquals(1, session.party().size());
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
        session.start(AdventureId.generate(), UUID.randomUUID());

        assertEquals(List.of(sheetId), session.complete());
        assertEquals(AdventureSession.Status.COMPLETED, session.status());
        assertThrows(IllegalStateException.class, () -> session.addPartyMember(
                new AdventurePartyMember(new CharacterSheetId(UUID.randomUUID()), ControlMode.DIRECT, true, true, true, true, true, true)));

        AdventureSession deleted = configuredSession();
        deleted.addPartyMember(new AdventurePartyMember(sheetId, ControlMode.DIRECT, true, true, true, true, true, true));
        deleted.start(AdventureId.generate(), UUID.randomUUID());
        assertEquals(List.of(sheetId), deleted.delete());
        assertEquals(AdventureSession.Status.DELETED, deleted.status());
    }
}
