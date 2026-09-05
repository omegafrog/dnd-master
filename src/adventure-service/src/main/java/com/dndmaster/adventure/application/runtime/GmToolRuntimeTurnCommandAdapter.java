package com.dndmaster.adventure.application.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Bridges a recovered RuntimeTurn command to the owning tool gateway. */
public final class GmToolRuntimeTurnCommandAdapter implements RuntimeTurnCommandAdapter {
    private final GmToolGateway gateway;
    private final ObjectMapper mapper;

    public GmToolRuntimeTurnCommandAdapter(GmToolGateway gateway, ObjectMapper mapper) {
        this.gateway = java.util.Objects.requireNonNull(gateway, "tool gateway must not be null");
        this.mapper = java.util.Objects.requireNonNull(mapper, "object mapper must not be null");
    }

    @Override public RuntimeTurnCommandExecution execute(RuntimeTurnCommand command) {
        TurnCapability capability = TurnCapability.issue(command.sessionId(), command.turnId(), command.ownerPlayerId(),
                Set.of(command.commandType()), Instant.now().plusSeconds(300), UUID.randomUUID());
        try {
            GmToolOutcome outcome = gateway.invoke(capability, new GmToolInvocation(command.commandId(), command.sessionId(),
                    command.turnId(), command.ownerPlayerId(), command.commandType(), command.payloadJson(), executionContext(command)));
            if (outcome.status() == GmToolOutcome.Status.UNKNOWN) {
                Optional<GmToolOutcome> known = gateway.query(command.commandType(), command.commandId());
                if (known.isPresent()) outcome = known.get();
            }
            return switch (outcome.status()) {
                case COMPLETED -> RuntimeTurnCommandExecution.done(outcome.value());
                case UNKNOWN -> RuntimeTurnCommandExecution.transientFailure(outcome.value());
                case REJECTED, REQUIRES_CHOICE -> RuntimeTurnCommandExecution.permanentFailure(outcome.value());
            };
        } finally {
            gateway.revoke(capability);
        }
    }

    private GmToolExecutionContext executionContext(RuntimeTurnCommand command) {
        String value = command.targetContext();
        if (!value.startsWith("adventureId=")) return null;
        try {
            String[] parts = value.split(";");
            return new GmToolExecutionContext(UUID.fromString(parts[0].substring("adventureId=".length())),
                    UUID.fromString(parts[1].substring("ruleSetId=".length())),
                    Long.parseLong(parts[2].substring("adventureVersion=".length())));
        } catch (RuntimeException malformed) {
            throw new IllegalArgumentException("invalid runtime command target context", malformed);
        }
    }
}
