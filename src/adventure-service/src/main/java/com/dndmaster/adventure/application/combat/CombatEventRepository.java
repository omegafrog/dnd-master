package com.dndmaster.adventure.application.combat;

import com.dndmaster.adventure.domain.combat.CombatEvent;
import java.util.List;
import java.util.UUID;
import java.util.Optional;

public interface CombatEventRepository {
    void append(CombatEvent event);
    List<CombatEvent> after(UUID encounterId, long sequence);

    default Optional<CombatEvent> finalSummary(UUID encounterId) {
        return after(encounterId, -1).stream().filter(event -> "COMBAT_ENDED".equals(event.eventType())).findFirst();
    }
}
