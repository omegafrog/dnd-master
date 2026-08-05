package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.runtime.GmTurn;
import java.util.Optional;
import java.util.UUID;

public interface GmTurnRepository {
    Optional<GmTurn> findByCommandId(UUID commandId);
    void save(GmTurn turn, UUID adventureId);
}
