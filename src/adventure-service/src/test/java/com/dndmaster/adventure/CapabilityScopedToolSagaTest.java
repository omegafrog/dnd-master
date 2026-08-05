package com.dndmaster.adventure;

import com.dndmaster.adventure.application.runtime.*;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CapabilityScopedToolSagaTest {
    private static final UUID SESSION = UUID.randomUUID();
    private static final UUID TURN = UUID.randomUUID();
    private static final UUID OWNER = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-08-05T00:00:00Z");

    @Test
    void capabilityRejectsExpiredCrossScopeAndUnauthorizedCallsBeforeDispatch() {
        TurnCapability capability = TurnCapability.issue(SESSION, TURN, OWNER, Set.of("dice.roll"),
                NOW.plusSeconds(60), UUID.randomUUID());
        GmToolGatewayService gateway = new GmToolGatewayService(
                Set.of(GmToolDefinition.of("dice.roll", "{}", invocation -> GmToolOutcome.completed("rolled"))),
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertThrows(ToolAuthorizationException.class, () -> gateway.invoke(capability,
                new GmToolInvocation(UUID.randomUUID(), SESSION, TURN, OWNER, "character.update", "{}")));
        assertThrows(ToolAuthorizationException.class, () -> gateway.invoke(capability,
                new GmToolInvocation(UUID.randomUUID(), UUID.randomUUID(), TURN, OWNER, "dice.roll", "{}")));
        assertThrows(ToolAuthorizationException.class, () -> gateway.invoke(
                capability.withExpiry(NOW.minusSeconds(1)),
                new GmToolInvocation(UUID.randomUUID(), SESSION, TURN, OWNER, "dice.roll", "{}")));
    }

    @Test
    void gatewayHasExplicitRegistryAndDoesNotDispatchUnknownTools() {
        GmToolGatewayService gateway = new GmToolGatewayService(Set.of(), Clock.fixed(NOW, ZoneOffset.UTC));
        TurnCapability capability = TurnCapability.issue(SESSION, TURN, OWNER, Set.of("shell.exec"), NOW.plusSeconds(60), UUID.randomUUID());
        assertThrows(UnknownToolException.class, () -> gateway.invoke(capability,
                new GmToolInvocation(UUID.randomUUID(), SESSION, TURN, OWNER, "shell.exec", "{}")));
    }

    @Test
    void sagaReplaysSameFingerprintAndRejectsMismatch() {
        InMemoryRuntimeCommandJournal journal = new InMemoryRuntimeCommandJournal();
        RuntimeCommandSagaApplicationService saga = new RuntimeCommandSagaApplicationService(journal);
        UUID commandId = UUID.randomUUID();
        RuntimeCommandOutcome first = saga.execute(new RuntimeCommandRequest(commandId, SESSION, TURN, OWNER, "dice.roll", "d20"),
                request -> RuntimeCommandOutcome.applied("20", 3));
        assertEquals(first, saga.execute(new RuntimeCommandRequest(commandId, SESSION, TURN, OWNER, "dice.roll", "d20"),
                request -> fail("replay must not dispatch")));
        assertThrows(CommandFingerprintConflictException.class, () -> saga.execute(
                new RuntimeCommandRequest(commandId, SESSION, TURN, OWNER, "dice.roll", "d6"), request -> fail()));
    }

    @Test
    void sagaQueriesUnknownOutcomeByCommandIdBeforeRetrying() {
        InMemoryRuntimeCommandJournal journal = new InMemoryRuntimeCommandJournal();
        RuntimeCommandSagaApplicationService saga = new RuntimeCommandSagaApplicationService(journal);
        UUID commandId = UUID.randomUUID();
        journal.record(new RuntimeCommandJournalEntry(commandId, SESSION, TURN, OWNER, "dice.roll", "d20",
                RuntimeCommandStatus.UNKNOWN, null, 0));
        RuntimeCommandOutcome recovered = saga.resume(commandId, request -> fail("must query owning system first"),
                id -> RuntimeCommandOutcome.applied("already-rolled", 4));
        assertEquals("already-rolled", recovered.value());
        assertEquals(RuntimeCommandStatus.APPLIED, journal.find(commandId).orElseThrow().status());
    }

    @Test
    void toolLoopAllowsOneRepairAndBoundsCalls() {
        TurnCapability capability = TurnCapability.issue(SESSION, TURN, OWNER, Set.of("dice.roll"), NOW.plusSeconds(60), UUID.randomUUID());
        GmToolGateway gateway = new GmToolGateway() {
            int calls;
            public GmToolOutcome invoke(TurnCapability ignored, GmToolInvocation invocation) {
                if (++calls == 1) throw new IllegalArgumentException("bad args");
                return GmToolOutcome.completed("ok");
            }
        };
        GmToolExecutionLoop.Result result = new GmToolExecutionLoop(gateway, 2).act(capability,
                List.of(new GmToolExecutionLoop.PlannedToolCall(new GmToolInvocation(UUID.randomUUID(), SESSION, TURN, OWNER, "dice.roll", "{}"), true)),
                ignored -> ignored);
        assertTrue(result.repaired());
        assertEquals(2, result.calls());
    }
}
