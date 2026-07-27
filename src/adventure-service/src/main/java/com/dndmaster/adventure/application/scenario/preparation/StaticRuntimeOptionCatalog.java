package com.dndmaster.adventure.application.scenario.preparation;

import com.dndmaster.adventure.domain.scenario.OwnerPlayerId;
import java.util.Objects;

public final class StaticRuntimeOptionCatalog implements RuntimeOptionCatalogPort {
    @Override
    public RuntimeOptionsView read(OwnerPlayerId ownerPlayerId) {
        Objects.requireNonNull(ownerPlayerId, "owner player id must not be null");
        return RuntimeOptionsView.defaults();
    }
}
