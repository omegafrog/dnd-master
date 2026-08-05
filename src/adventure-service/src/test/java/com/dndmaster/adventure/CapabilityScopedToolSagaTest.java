package com.dndmaster.adventure;

import com.dndmaster.adventure.application.runtime.*;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
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
                if (++calls == 1) throw new ToolArgumentInvalidException("bad args");
                return GmToolOutcome.completed("ok");
            }
        };
        GmToolExecutionLoop.Result result = new GmToolExecutionLoop(gateway, 2).act(capability,
                List.of(new GmToolExecutionLoop.PlannedToolCall(new GmToolInvocation(UUID.randomUUID(), SESSION, TURN, OWNER, "dice.roll", "{}"), true)),
                ignored -> ignored);
        assertTrue(result.repaired());
        assertEquals(2, result.calls());
    }

    @Test
    void gatewayRejectsArgumentsBeforeHandlerAndRevokedCapability() {
        AtomicInteger dispatched = new AtomicInteger();
        GmToolGatewayService gateway = new GmToolGatewayService(Set.of(
                GmToolDefinition.of("dice.roll", "{\"type\":\"object\",\"required\":[\"expression\"],\"properties\":{\"expression\":{\"type\":\"string\"}}}", invocation -> {
                    dispatched.incrementAndGet(); return GmToolOutcome.completed("ok");
                })), Clock.fixed(NOW, ZoneOffset.UTC));
        TurnCapability capability = TurnCapability.issue(SESSION, TURN, OWNER, Set.of("dice.roll"), NOW.plusSeconds(60), UUID.randomUUID());
        assertThrows(ToolArgumentInvalidException.class, () -> gateway.invoke(capability,
                new GmToolInvocation(UUID.randomUUID(), SESSION, TURN, OWNER, "dice.roll", "{}")));
        assertEquals(0, dispatched.get());
        gateway.revoke(capability);
        assertThrows(ToolAuthorizationException.class, () -> gateway.invoke(capability,
                new GmToolInvocation(UUID.randomUUID(), SESSION, TURN, OWNER, "dice.roll", "{\"expression\":\"d20\"}")));
    }

    @Test
    void journalClaimAllowsOnlyOneConcurrentDispatcher() throws Exception {
        InMemoryRuntimeCommandJournal journal = new InMemoryRuntimeCommandJournal();
        RuntimeCommandSagaApplicationService saga = new RuntimeCommandSagaApplicationService(journal);
        UUID commandId = UUID.randomUUID();
        RuntimeCommandRequest request = new RuntimeCommandRequest(commandId, SESSION, TURN, OWNER, "dice.roll", "d20");
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger dispatched = new AtomicInteger();
        var tasks = java.util.stream.IntStream.range(0, 8).mapToObj(ignored -> (java.util.concurrent.Callable<RuntimeCommandOutcome>) () -> {
            start.await();
            try { return saga.execute(request, value -> { dispatched.incrementAndGet(); return RuntimeCommandOutcome.applied("ok", 1); }); }
            catch (CommandInProgressException expected) { return null; }
        }).toList();
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(8)) {
            var futures = tasks.stream().map(pool::submit).toList();
            start.countDown();
            for (var future : futures) future.get();
        }
        assertEquals(1, dispatched.get());
    }

    @Test
    void officialDiceAndCharacterToolsUseExplicitPorts() {
        AtomicInteger dice = new AtomicInteger();
        AtomicInteger character = new AtomicInteger();
        GmToolGatewayService gateway = new GmToolGatewayService(
                OfficialGmToolRegistry.definitions(invocation -> { dice.incrementAndGet(); return GmToolOutcome.completed("rolled"); },
                        invocation -> { character.incrementAndGet(); return GmToolOutcome.completed("updated"); }), Clock.fixed(NOW, ZoneOffset.UTC));
        TurnCapability capability = TurnCapability.issue(SESSION, TURN, OWNER, Set.of("dice.roll", "character.update"), NOW.plusSeconds(60), UUID.randomUUID());
        String diceArguments = "{\"adventureId\":\"" + UUID.randomUUID() + "\",\"ruleSetId\":\"" + UUID.randomUUID() + "\",\"scope\":\"PLAYER_ACTION\",\"count\":1,\"sides\":20,\"modifier\":0,\"sessionId\":\"" + SESSION + "\",\"turnId\":\"" + TURN + "\",\"commandId\":\"" + UUID.randomUUID() + "\",\"expectedVersion\":0}";
        String characterArguments = "{\"characterSheetId\":\"" + UUID.randomUUID() + "\",\"expectedVersion\":0,\"edition\":\"DND_5E_2024\",\"characterName\":\"Aria\",\"level\":1,\"inspiration\":false,\"race\":\"Elf\",\"characterClass\":\"Wizard\",\"background\":\"Sage\"}";
        gateway.invoke(capability, new GmToolInvocation(UUID.randomUUID(), SESSION, TURN, OWNER, "dice.roll", diceArguments));
        gateway.invoke(capability, new GmToolInvocation(UUID.randomUUID(), SESSION, TURN, OWNER, "character.update", characterArguments));
        assertEquals(1, dice.get()); assertEquals(1, character.get());
    }
}
