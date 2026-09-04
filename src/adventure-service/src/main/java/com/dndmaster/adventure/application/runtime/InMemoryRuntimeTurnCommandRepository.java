package com.dndmaster.adventure.application.runtime;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryRuntimeTurnCommandRepository implements RuntimeTurnCommandRepository {
    private final ConcurrentHashMap<UUID, RuntimeTurnCommand> commands = new ConcurrentHashMap<>();

    @Override public Optional<RuntimeTurnCommand> findByCommandId(UUID commandId) {
        return Optional.ofNullable(commands.get(commandId));
    }

    @Override public Optional<RuntimeTurnCommand> findByIdempotencyKey(String idempotencyKey) {
        return commands.values().stream().filter(command -> command.idempotencyKey().equals(idempotencyKey)).findFirst();
    }

    @Override public List<RuntimeTurnCommand> findByTurnId(UUID turnId) {
        return commands.values().stream().filter(command -> command.turnId().equals(turnId))
                .sorted(Comparator.comparingInt(RuntimeTurnCommand::executionOrder)
                        .thenComparing(RuntimeTurnCommand::commandId)).toList();
    }

    @Override public synchronized void save(RuntimeTurnCommand command) {
        RuntimeTurnCommand previous = commands.values().stream()
                .filter(value -> value.turnId().equals(command.turnId())
                        && value.executionOrder() == command.executionOrder()
                        && !value.commandId().equals(command.commandId()))
                .findFirst().orElse(null);
        if (previous != null) throw new IllegalStateException("duplicate runtime command execution order");
        RuntimeTurnCommand sameKey = findByIdempotencyKey(command.idempotencyKey()).orElse(null);
        if (sameKey != null && !sameKey.commandId().equals(command.commandId())) {
            throw new IllegalStateException("duplicate runtime command idempotency key");
        }
        commands.put(command.commandId(), command);
    }
}
