package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.application.combat.MovementFollowUpCommand;
import com.dndmaster.adventure.domain.runtime.event.SessionEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

/** Consumes the durable movement event and records its typed Runtime continuation. */
public final class MovementFollowUpRuntimeConsumer {
    private final SessionEventRepository events;
    private final RuntimeTurnCommandRepository commands;
    private final ObjectMapper objectMapper;
    private final MovementFollowUpPolicy policy;
    private final RuntimeContinuationPort continuationPort;

    public MovementFollowUpRuntimeConsumer(SessionEventRepository events, RuntimeTurnCommandRepository commands,
            ObjectMapper objectMapper, MovementFollowUpPolicy policy) {
        this(events, commands, objectMapper, policy, RuntimeContinuationHandlerRegistry.standard());
    }

    public MovementFollowUpRuntimeConsumer(SessionEventRepository events, RuntimeTurnCommandRepository commands,
            ObjectMapper objectMapper, MovementFollowUpPolicy policy, RuntimeContinuationPort continuationPort) {
        this.events = Objects.requireNonNull(events);
        this.commands = Objects.requireNonNull(commands);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.policy = Objects.requireNonNull(policy);
        this.continuationPort = Objects.requireNonNull(continuationPort);
    }

    public synchronized MovementFollowUpPort.Result consume(RuntimeTurnCommand source, MovementFollowUpCommand expected) {
        try {
            SessionEvent event = events.after(source.sessionId(), -1).stream()
                    .filter(candidate -> candidate.eventId().equals(expected.commandId()))
                    .findFirst().orElseThrow(() -> new IllegalStateException("movement follow-up event is not durable"));
            if (!"MOVEMENT_FOLLOW_UP".equals(event.type())) {
                throw new IllegalStateException("unexpected movement follow-up event type");
            }
            MovementFollowUpCommand followUp = objectMapper.readValue(event.payload(), MovementFollowUpCommand.class);
            if (!followUp.equals(expected)) throw new IllegalStateException("movement follow-up event payload mismatch");
            MovementFollowUpCommand.Kind kind = policy.determine(followUp.trigger());
            UUID continuationId = UUID.nameUUIDFromBytes(
                    ("movement-continuation:" + followUp.commandId()).getBytes(StandardCharsets.UTF_8));
            RuntimeTurnCommand existing = commands.findByCommandId(continuationId).orElse(null);
            if (existing != null && existing.executionStatus() == RuntimeTurnCommand.ExecutionStatus.DONE) {
                return MovementFollowUpPort.Result.done(existing.outcomeJson());
            }
            int order = commands.findByTurnId(source.turnId()).stream()
                    .mapToInt(RuntimeTurnCommand::executionOrder).max().orElse(source.executionOrder()) + 1;
            String payload = objectMapper.writeValueAsString(new Continuation(kind, followUp.trigger(), followUp.operationId(), source.turnId()));
            RuntimeTurnCommand continuation = existing == null
                    ? RuntimeTurnCommand.create(source.turnId(), continuationId, source.adventureId(), source.sessionId(),
                            source.ownerPlayerId(), source.targetContext(),
                            "movement.continuation." + kind.name().toLowerCase(java.util.Locale.ROOT), payload, order)
                    : existing;
            if (existing == null) commands.save(continuation);
            RuntimeContinuationOutcome outcome;
            try {
                outcome = continuationPort.execute(continuation,
                        new Continuation(kind, followUp.trigger(), followUp.operationId(), source.turnId()));
            } catch (RuntimeException failure) {
                commands.save(continuation.failed(failure.getMessage(), failure.getMessage()));
                return MovementFollowUpPort.Result.retry(failure.getMessage());
            }
            if (outcome.status() == RuntimeContinuationOutcome.Status.RETRY) {
                commands.save(continuation.failed(outcome.value(), outcome.value()));
                return MovementFollowUpPort.Result.retry(outcome.value());
            }
            commands.save(continuation.done(outcome.value()));
            return MovementFollowUpPort.Result.done(outcome.value());
        } catch (IOException | RuntimeException failure) {
            return MovementFollowUpPort.Result.retry(failure.getMessage());
        }
    }

    public record Continuation(MovementFollowUpCommand.Kind kind, String trigger, UUID operationId, UUID turnId) { }
}
