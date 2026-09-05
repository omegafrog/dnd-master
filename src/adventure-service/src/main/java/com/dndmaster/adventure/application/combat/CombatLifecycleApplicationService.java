package com.dndmaster.adventure.application.combat;

import com.dndmaster.adventure.domain.combat.*;
import java.util.List;
import java.util.UUID;

/** Owns the local combat-entry transaction seam; later actions do not belong here. */
public final class CombatLifecycleApplicationService {
    private final CombatEncounterRepository repository;
    public CombatLifecycleApplicationService(CombatEncounterRepository repository) { this.repository = repository; }
    public CombatEncounter startFromCommittedGmTurn(UUID adventureId, boolean gmTurnCommitted,
                                                    List<CombatParticipant> participants) {
        CombatStartPolicy.requireNoActiveEncounter(adventureId,
                repository.findActive(adventureId).stream().toList());
        return repository.save(CombatStartPolicy.startFromCommittedGmTurn(gmTurnCommitted, adventureId, participants));
    }
}
