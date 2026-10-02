package com.dndmaster.adventure.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.AdventurePartyMember;
import com.dndmaster.adventure.domain.adventure.AdventureSession;
import com.dndmaster.adventure.domain.adventure.CharacterSheetId;
import com.dndmaster.adventure.domain.adventure.ControlMode;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import com.dndmaster.adventure.domain.adventure.SessionId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AdventureSessionCharacterPolicyTest {
    @Test
    void started_party_member_can_open_its_sheet_while_initial_values_remain_frozen() {
        UUID sheetId = UUID.randomUUID();
        var session = sessionWithPartyMember(AdventureSession.Status.STARTED, sheetId);

        AdventureSessionController.CharacterPolicyView policy =
                AdventureSessionController.characterPolicyFor(session, sheetId);

        assertThat(policy.sessionActive()).isTrue();
        assertThat(policy.acceptingCharacterSheets()).isFalse();
        assertThat(policy.runtimeMutationsAllowed()).isTrue();
        assertThat(policy.nameMutable()).isFalse();
    }

    @Test
    void starting_party_member_sheet_remains_active_until_start_finishes() {
        UUID sheetId = UUID.randomUUID();
        var session = sessionWithPartyMember(AdventureSession.Status.STARTING, sheetId);

        AdventureSessionController.CharacterPolicyView policy =
                AdventureSessionController.characterPolicyFor(session, sheetId);

        assertThat(policy.sessionActive()).isTrue();
        assertThat(policy.runtimeMutationsAllowed()).isFalse();
    }

    @Test
    void completed_sessions_are_not_active_for_character_sheet_reads() {
        UUID sheetId = UUID.randomUUID();
        var session = sessionWithPartyMember(AdventureSession.Status.COMPLETED, sheetId);

        AdventureSessionController.CharacterPolicyView policy =
                AdventureSessionController.characterPolicyFor(session, sheetId);

        assertThat(policy.sessionActive()).isFalse();
        assertThat(policy.runtimeMutationsAllowed()).isFalse();
    }

    private static AdventureSession sessionWithPartyMember(AdventureSession.Status status, UUID sheetId) {
        UUID sessionUuid = UUID.randomUUID();
        UUID adventureUuid = UUID.randomUUID();
        UUID packageId = UUID.randomUUID();
        var member = new AdventurePartyMember(new CharacterSheetId(sheetId), ControlMode.DIRECT,
                false, false, false, false, false, false);
        boolean started = status != AdventureSession.Status.DRAFT;
        return AdventureSession.rehydrate(new SessionId(sessionUuid), new OwnerPlayerId(UUID.randomUUID()),
                packageId, 1, packageId, 1, "DND_5E_2014", 4, List.of(member), null, status,
                started ? new AdventureId(adventureUuid) : null, started ? UUID.randomUUID() : null, 1);
    }
}
