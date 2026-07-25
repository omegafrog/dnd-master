package com.dndmaster.adventure.application.scenario.preparation;

import com.dndmaster.adventure.domain.scenario.OwnerPlayerId;

public interface RuntimeOptionCatalogPort {
    RuntimeOptionsView read(OwnerPlayerId ownerPlayerId);
}
