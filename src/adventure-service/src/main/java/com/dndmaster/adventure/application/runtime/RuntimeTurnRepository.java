package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.adventure.AdventureId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RuntimeTurnRepository {
    Optional<RuntimeTurn> findByTurnId(UUID turnId);
    Optional<RuntimeTurn> findByCommandId(UUID commandId);
    List<RuntimeTurn> findAllByAdventureId(AdventureId adventureId);
    void save(RuntimeTurn turn);
}
