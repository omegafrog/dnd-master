package com.dndmaster.adventure.application.runtime;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RuntimeTurnCommandRepository {
    Optional<RuntimeTurnCommand> findByCommandId(UUID commandId);
    default Optional<RuntimeTurnCommand> findByIdempotencyKey(String idempotencyKey) { return Optional.empty(); }
    List<RuntimeTurnCommand> findByTurnId(UUID turnId);
    void save(RuntimeTurnCommand command);

    default void saveAll(List<RuntimeTurnCommand> commands) {
        commands.forEach(this::save);
    }
}
