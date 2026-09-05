package com.dndmaster.adventure.domain.combat;

import java.util.List;
import java.util.UUID;

public final class CombatStartPolicy {
    private CombatStartPolicy() {}
    public static CombatEncounter startFromCommittedGmTurn(boolean gmTurnCommitted, UUID adventureId,
                                                            List<CombatParticipant> participants) {
        if (!gmTurnCommitted) throw new CombatStartRejectedException("combat requires committed GM turn");
        var order = InitiativeOrderPolicy.order(participants);
        return new CombatEncounter(UUID.randomUUID(), adventureId, CombatEncounter.Status.ACTIVE, 1,
                order.participantIds().get(0), participants, 1, 0);
    }
    public static void requireNoActiveEncounter(UUID adventureId, List<CombatEncounter> activeEncounters) {
        if (activeEncounters.stream().anyMatch(e -> e.adventureId().equals(adventureId)
                && e.status() != CombatEncounter.Status.ENDED)) {
            throw new ActiveCombatEncounterException("adventure already has an active combat encounter");
        }
    }
}
