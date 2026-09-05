package com.dndmaster.adventure.application.combat;

import com.dndmaster.adventure.domain.combat.CombatEvent;
import java.util.List;
import java.util.UUID;

public interface CombatEventRepository {
    void append(CombatEvent event);
    List<CombatEvent> after(UUID encounterId, long sequence);
}
