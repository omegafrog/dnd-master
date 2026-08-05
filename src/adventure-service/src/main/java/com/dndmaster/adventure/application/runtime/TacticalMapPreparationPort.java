package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.scenario.MapDefinition;
import java.util.UUID;

/** Cross-context seam for materializing a compiled map in Combat Map. */
public interface TacticalMapPreparationPort {
    UUID prepare(UUID adventureId, UUID ownerPlayerId, MapDefinition definition);
}
