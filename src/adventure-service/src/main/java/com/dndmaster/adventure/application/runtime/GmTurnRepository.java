package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.runtime.GmTurn;
import java.util.Optional;
import java.util.UUID;

public interface GmTurnRepository {
    default void lockAdventure(UUID adventureId) {}
    default void recoverActive(UUID adventureId, UUID commandId, String failure) {}
    Optional<GmTurn> findByCommandId(UUID commandId);
    void save(GmTurn turn, UUID adventureId);
}
