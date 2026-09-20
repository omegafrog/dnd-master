package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.adventure.application.runtime.RuntimeContinuationCommandPort;
import com.dndmaster.adventure.application.runtime.CorruptRuntimeContinuationOutcomeException;
import com.dndmaster.adventure.application.runtime.RuntimeTurnCommand;
import com.dndmaster.adventure.application.runtime.RuntimeTurnCommandExecution;
import com.dndmaster.adventure.application.runtime.TypedRuntimeContinuationCommandAdapter;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class TypedRuntimeContinuationCommandAdapterTest {
    @Test
    void rejects_missing_or_malformed_continuation_ids_as_permanent_without_calling_port() {
        AtomicReference<RuntimeContinuationCommandPort.ContinuationCommand> called = new AtomicReference<>();
        RuntimeContinuationCommandPort port = port(called);
        RuntimeTurnCommand command = command("{\"kind\":\"COMBAT\",\"trigger\":\"HOSTILE_OBSERVED\",\"operationId\":\"not-a-uuid\",\"turnId\":\""
                + UUID.randomUUID() + "\",\"hostileTokenId\":null}");

        RuntimeTurnCommandExecution result = adapter(port).execute(command);

        assertEquals(RuntimeTurnCommandExecution.Status.PERMANENT_FAILURE, result.status());
        assertFalse(called.get() != null);
    }

    @Test
    void rejects_a_payload_turn_that_does_not_match_the_command_turn() {
        AtomicReference<RuntimeContinuationCommandPort.ContinuationCommand> called = new AtomicReference<>();
        UUID commandTurnId = UUID.randomUUID();
        UUID payloadTurnId = UUID.randomUUID();
        RuntimeTurnCommand command = RuntimeTurnCommand.create(commandTurnId, UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), "external", "movement.continuation.combat",
                payload(UUID.randomUUID(), payloadTurnId, UUID.randomUUID()), 1);

        RuntimeTurnCommandExecution result = adapter(port(called)).execute(command);

        assertEquals(RuntimeTurnCommandExecution.Status.PERMANENT_FAILURE, result.status());
        assertEquals("continuation payload turn id does not match command turn id", result.value());
        assertEquals(null, called.get());
    }

    @Test
    void preserves_the_valid_payload_turn_id_when_dispatching() {
        AtomicReference<RuntimeContinuationCommandPort.ContinuationCommand> called = new AtomicReference<>();
        UUID turnId = UUID.randomUUID();
        RuntimeTurnCommand command = RuntimeTurnCommand.create(turnId, UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), "external", "movement.continuation.combat",
                payload(UUID.randomUUID(), turnId, UUID.randomUUID()), 1);

        RuntimeTurnCommandExecution result = adapter(port(called)).execute(command);

        assertEquals(RuntimeTurnCommandExecution.Status.DONE, result.status());
        assertEquals(turnId, called.get().continuation().turnId());
    }

    @Test
    void maps_provider_failures_to_retry_without_treating_them_as_payload_corruption() {
        UUID turnId = UUID.randomUUID();
        RuntimeTurnCommand command = RuntimeTurnCommand.create(turnId, UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), "external", "movement.continuation.combat",
                payload(UUID.randomUUID(), turnId, UUID.randomUUID()), 1);

        RuntimeTurnCommandExecution result = adapter(new RuntimeContinuationCommandPort() {
            @Override public RuntimeTurnCommandExecution combat(ContinuationCommand command) {
                throw new IllegalStateException("provider unavailable");
            }
        }).execute(command);

        assertEquals(RuntimeTurnCommandExecution.Status.TRANSIENT_FAILURE, result.status());
        assertTrue(result.value().contains("provider unavailable"));
    }

    @Test
    void maps_corrupt_persisted_continuation_outcomes_to_permanent_failure() {
        UUID turnId = UUID.randomUUID();
        RuntimeTurnCommand command = RuntimeTurnCommand.create(turnId, UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), "external", "movement.continuation.combat",
                payload(UUID.randomUUID(), turnId, UUID.randomUUID()), 1);
        RuntimeTurnCommandExecution result = adapter(new RuntimeContinuationCommandPort() {
            @Override public RuntimeTurnCommandExecution combat(ContinuationCommand ignored) {
                throw new CorruptRuntimeContinuationOutcomeException("corrupt persisted outcome", null);
            }
        }).execute(command);

        assertEquals(RuntimeTurnCommandExecution.Status.PERMANENT_FAILURE, result.status());
    }

    @Test
    void rejects_noncanonical_continuation_payload_text_permanently() {
        UUID turnId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        UUID hostileTokenId = UUID.randomUUID();
        String canonical = payload(operationId, turnId, hostileTokenId);
        for (String nonCanonical : new String[] {
                " " + canonical,
                canonical.replace("\"trigger\"", " \"trigger\""),
                "{\"trigger\":\"HOSTILE_OBSERVED\",\"kind\":\"COMBAT\",\"operationId\":\""
                        + operationId + "\",\"turnId\":\"" + turnId + "\",\"hostileTokenId\":\""
                        + hostileTokenId + "\"}"
        }) {
            RuntimeTurnCommandExecution result = adapter(port(new AtomicReference<>())).execute(
                    RuntimeTurnCommand.create(turnId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                            UUID.randomUUID(), "external", "movement.continuation.combat", nonCanonical, 1));
            assertEquals(RuntimeTurnCommandExecution.Status.PERMANENT_FAILURE, result.status());
        }
    }

    private static RuntimeContinuationCommandPort port(AtomicReference<RuntimeContinuationCommandPort.ContinuationCommand> called) {
        return new RuntimeContinuationCommandPort() {
            @Override public RuntimeTurnCommandExecution combat(ContinuationCommand command) {
                called.set(command);
                return RuntimeTurnCommandExecution.done("ok");
            }
        };
    }

    private static TypedRuntimeContinuationCommandAdapter adapter(RuntimeContinuationCommandPort port) {
        return new TypedRuntimeContinuationCommandAdapter(TypedRuntimeContinuationCommandAdapter.Kind.COMBAT, port);
    }

    private static RuntimeTurnCommand command(String payload) {
        return RuntimeTurnCommand.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "external", "movement.continuation.combat", payload, 1);
    }

    private static String payload(UUID operationId, UUID turnId, UUID hostileTokenId) {
        return "{\"kind\":\"COMBAT\",\"trigger\":\"HOSTILE_OBSERVED\",\"operationId\":\"" + operationId
                + "\",\"turnId\":\"" + turnId + "\",\"hostileTokenId\":\"" + hostileTokenId + "\"}";
    }

}
