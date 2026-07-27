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
    void permits_pre_start_party_changes_but_rejects_party_over_storybook_limit() {
        AdventureSession session = AdventureSession.create(
                SessionId.generate(), new OwnerPlayerId(UUID.randomUUID()), UUID.randomUUID(), 1, 1);
        AdventurePartyMember first = new AdventurePartyMember(
                new CharacterSheetId(UUID.randomUUID()), ControlMode.DIRECT, true, false, true, false, true, false);

        session.addPartyMember(first);

        assertEquals(1, session.party().size());
        assertThrows(IllegalStateException.class, () -> session.addPartyMember(new AdventurePartyMember(
                new CharacterSheetId(UUID.randomUUID()), ControlMode.AGENT, true, true, true, true, true, true)));
        session.removePartyMember(first.characterSheetId());
        assertEquals(0, session.party().size());
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
