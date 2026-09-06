package com.dndmaster.adventure.application.combat;

import com.dndmaster.adventure.domain.combat.CombatEncounter;
import java.util.Optional;
import java.util.UUID;

public interface CombatEncounterRepository {
    Optional<CombatEncounter> findActive(UUID adventureId);
    default Optional<CombatEncounter> findByEncounterId(UUID encounterId) { return Optional.empty(); }
    default Optional<CombatEncounter> findLatestEndedByAdventure(UUID adventureId) { return Optional.empty(); }
    CombatEncounter save(CombatEncounter encounter);

    default CombatEncounter save(CombatEncounter encounter, long expectedVersion) {
        return save(encounter);
    }
}
