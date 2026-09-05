package com.dndmaster.adventure.application.combat;

import com.dndmaster.adventure.domain.combat.CombatEncounter;
import java.util.Optional;
import java.util.UUID;

public interface CombatEncounterRepository {
    Optional<CombatEncounter> findActive(UUID adventureId);
    CombatEncounter save(CombatEncounter encounter);
}
