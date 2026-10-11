package com.dndmaster.adventure.dice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.adventure.dice.application.*;
import com.dndmaster.adventure.dice.domain.*;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

class DiceRollApplicationServiceTest {
    @Test
    void deterministicRandomProducesValidatedFacesAndTotal() {
        RecordingRepository repository = new RecordingRepository();
        DiceRollApplicationService service = new DiceRollApplicationService(repository, new FakeRandom(0, 5));

        DiceRoll roll = service.executePlayerRoll(command(RollScope.PLAYER_ACTION, new DiceExpression(2, 6, 3)));

        assertEquals(List.of(1, 6), roll.result().orElseThrow().faces());
        assertEquals(10, roll.result().orElseThrow().total());
        assertEquals(List.of(roll), repository.saved);
    }

    @Test
    void same_command_id_replays_saved_roll_without_sampling_again() {
        RecordingRepository repository = new RecordingRepository();
        DiceRollApplicationService service = new DiceRollApplicationService(repository, new FakeRandom(0, 5));
        RollCommand command = command(RollScope.PLAYER_ACTION, new DiceExpression(2, 6, 3));

        DiceRoll first = service.executePlayerRoll(command);
        DiceRoll second = service.executePlayerRoll(command);

        assertEquals(first, second);
        assertEquals(1, repository.saved.size());
    }

    @Test
    void concurrent_same_id_with_different_payload_returns_one_result_and_rejects_conflict() throws Exception {
        var barrier = new CyclicBarrier(2);
        var repository = new UniqueRepository();
        var service = new DiceRollApplicationService(repository, bound -> {
            try { barrier.await(); } catch (Exception failure) { throw new IllegalStateException(failure); }
            return 0;
        });
        RollCommand base = command(RollScope.PLAYER_ACTION, new DiceExpression(1, 20, 0));
        RollCommand conflicting = new RollCommand(base.adventureId(), base.ruleSetId(), base.scope(),
                new DiceExpression(1, 6, 0), base.sessionId(), base.turnId(), base.commandId(),
                base.expectedVersion(), base.ruleReference(), base.difficulty());
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> service.executePlayerRoll(base));
            var second = executor.submit(() -> service.executePlayerRoll(conflicting));
            int successes = 0;
            int conflicts = 0;
            for (var future : List.of(first, second)) {
                try { future.get(); successes++; }
                catch (ExecutionException failure) {
                    if (failure.getCause() instanceof IllegalStateException) conflicts++;
                    else throw failure;
                }
            }
            assertEquals(1, successes);
            assertEquals(1, conflicts);
            assertEquals(1, repository.saved.size());
        }
    }

    @Test
    void playerCanExecuteOnlyPlayerAction() {
        for (RollScope forbidden : List.of(RollScope.NPC, RollScope.ENEMY, RollScope.SECRET_CHECK)) {
            DiceRollApplicationService service = new DiceRollApplicationService(new RecordingRepository(), new FakeRandom(0));
            assertThrows(RollPermissionDeniedException.class, () -> service.executePlayerRoll(command(forbidden, die())));
        }
    }

    @Test
    void aiCanExecuteNpcEnemyAndSecretChecksButNotPlayerAction() {
        for (RollScope allowed : List.of(RollScope.NPC, RollScope.ENEMY, RollScope.SECRET_CHECK)) {
            DiceRollApplicationService service = new DiceRollApplicationService(new RecordingRepository(), new FakeRandom(1));
            assertEquals(allowed, service.executeAiRoll(command(allowed, die())).scope());
        }
        DiceRollApplicationService service = new DiceRollApplicationService(new RecordingRepository(), new FakeRandom(1));
        assertThrows(
                RollPermissionDeniedException.class,
                () -> service.executeAiRoll(command(RollScope.PLAYER_ACTION, die())));
    }

    @Test
    void aiCannot_replay_a_saved_player_roll() {
        RecordingRepository repository = new RecordingRepository();
        RollCommand command = command(RollScope.PLAYER_ACTION, die());
        DiceRoll saved = new DiceRoll(RollId.generate(), command.adventureId(), command.ruleSetId(), command.scope(),
                command.expression(), command.sessionId(), command.turnId(), command.commandId(), command.expectedVersion(),
                command.ruleReference(), command.difficulty());
        saved.recordBuiltInResult(DiceResult.forExpression(command.expression(), List.of(1)));
        repository.saved.add(saved);

        DiceRollApplicationService service = new DiceRollApplicationService(repository, new FakeRandom());

        assertThrows(RollPermissionDeniedException.class, () -> service.executeAiRoll(command));
    }

    @Test
    void conflicting_replay_diagnostics_include_safe_expected_and_actual_details() {
        var repository = new RecordingRepository();
        var service = new DiceRollApplicationService(repository, new FakeRandom(0));
        RollCommand original = command(RollScope.PLAYER_ACTION, new DiceExpression(1, 20, 0));
        RollCommand conflict = new RollCommand(original.adventureId(), original.ruleSetId(), original.scope(),
                new DiceExpression(1, 6, 0), original.sessionId(), original.turnId(), original.commandId(),
                original.expectedVersion(), original.ruleReference(), original.difficulty());
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory
                .getLogger(DiceRollApplicationService.class);
        var appender = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        String previous = System.getProperty(com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.FLAG);
        System.setProperty(com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.FLAG, "true");
        try {
            service.executePlayerRoll(original);
            assertThrows(IllegalStateException.class, () -> service.executePlayerRoll(conflict));
            assertTrue(appender.list.stream().map(ch.qos.logback.classic.spi.ILoggingEvent::getFormattedMessage)
                    .anyMatch(message -> message.contains("commandId=" + original.commandId())
                            && message.contains("expectedSides=6") && message.contains("actualSides=20")
                            && message.contains("result=conflict") && message.contains("outcome=failed")));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
            if (previous == null) System.clearProperty(com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.FLAG);
            else System.setProperty(com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.FLAG, previous);
        }
    }

    @Test
    void expressionAndResultInvariantsRejectInvalidValues() {
        assertThrows(IllegalArgumentException.class, () -> new DiceExpression(0, 6, 0));
        assertThrows(IllegalArgumentException.class, () -> new DiceExpression(1, 1, 0));
        DiceExpression expression = new DiceExpression(2, 6, 1);
        assertThrows(IllegalArgumentException.class, () -> DiceResult.forExpression(expression, List.of(1)));
        DiceRoll roll = new DiceRoll(
                RollId.generate(), adventure(), ruleSet(), RollScope.PLAYER_ACTION, expression,
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 0);
        assertThrows(
                IllegalArgumentException.class,
                () -> roll.recordBuiltInResult(new DiceResult(List.of(1, 2), 99)));
    }

    private static RollCommand command(RollScope scope, DiceExpression expression) {
        return new RollCommand(adventure(), ruleSet(), scope, expression, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 0,
                "dnd5e.perception", 10);
    }
    private static DiceExpression die() { return new DiceExpression(1, 20, 0); }
    private static AdventureId adventure() { return new AdventureId(UUID.randomUUID()); }
    private static RuleSetId ruleSet() { return new RuleSetId(UUID.randomUUID()); }

    private static final class FakeRandom implements DiceRandomPort {
        private final Deque<Integer> values = new ArrayDeque<>();
        private FakeRandom(Integer... values) { this.values.addAll(List.of(values)); }
        @Override public int nextInt(int bound) { return values.removeFirst(); }
    }

    private static final class RecordingRepository implements DiceRollRepository {
        private final List<DiceRoll> saved = new ArrayList<>();
        @Override public java.util.Optional<DiceRoll> findByCommandId(UUID commandId) {
            return saved.stream().filter(roll -> roll.commandId().equals(commandId)).findFirst();
        }
        @Override public void save(DiceRoll roll) { saved.add(roll); }
    }

    private static final class UniqueRepository implements DiceRollRepository {
        private final java.util.concurrent.ConcurrentHashMap<UUID, DiceRoll> saved = new java.util.concurrent.ConcurrentHashMap<>();
        @Override public java.util.Optional<DiceRoll> findByCommandId(UUID commandId) {
            return java.util.Optional.ofNullable(saved.get(commandId));
        }
        @Override public void save(DiceRoll roll) {
            if (saved.putIfAbsent(roll.commandId(), roll) != null) throw new IllegalStateException("unique command id");
        }
    }
}
