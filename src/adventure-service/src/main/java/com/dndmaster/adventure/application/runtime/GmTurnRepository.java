package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.runtime.GmTurn;
import java.util.Optional;
import java.util.UUID;

public interface GmTurnRepository {
    default void lockAdventure(UUID adventureId) {}
    Optional<GmTurn> findByCommandId(UUID commandId);
    default Optional<GmTurn> findByTurnId(UUID turnId) { return Optional.empty(); }
    default Optional<GmTurn> findByTurnIdAndAdventureId(UUID turnId, UUID adventureId) { return Optional.empty(); }
    void save(GmTurn turn, UUID adventureId);
}
