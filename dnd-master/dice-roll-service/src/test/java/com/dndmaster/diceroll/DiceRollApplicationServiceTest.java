package com.dndmaster.diceroll;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndmaster.diceroll.application.*;
import com.dndmaster.diceroll.domain.*;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
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
    void expressionAndResultInvariantsRejectInvalidValues() {
        assertThrows(IllegalArgumentException.class, () -> new DiceExpression(0, 6, 0));
        assertThrows(IllegalArgumentException.class, () -> new DiceExpression(1, 1, 0));
        DiceExpression expression = new DiceExpression(2, 6, 1);
        assertThrows(IllegalArgumentException.class, () -> DiceResult.forExpression(expression, List.of(1)));
        DiceRoll roll = new DiceRoll(
                RollId.generate(), adventure(), ruleSet(), RollScope.PLAYER_ACTION, expression);
        assertThrows(
                IllegalArgumentException.class,
                () -> roll.recordBuiltInResult(new DiceResult(List.of(1, 2), 99)));
    }

    private static RollCommand command(RollScope scope, DiceExpression expression) {
        return new RollCommand(adventure(), ruleSet(), scope, expression);
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
        @Override public void save(DiceRoll roll) { saved.add(roll); }
    }
}
