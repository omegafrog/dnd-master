package com.dndmaster.diceroll.application;

import com.dndmaster.diceroll.domain.DiceResult;
import com.dndmaster.diceroll.domain.DiceRoll;
import com.dndmaster.diceroll.domain.RollId;
import java.util.ArrayList;
import java.util.Objects;

public final class DiceRollApplicationService {
    private final DiceRollRepository repository;
    private final DiceRandomPort randomPort;

    public DiceRollApplicationService(DiceRollRepository repository, DiceRandomPort randomPort) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.randomPort = Objects.requireNonNull(randomPort, "random port must not be null");
    }

    public DiceRoll executePlayerRoll(RollCommand command) {
        DiceRoll roll = create(command);
        roll.authorizePlayerExecution();
        return execute(roll);
    }

    public DiceRoll executeAiRoll(RollCommand command) {
        DiceRoll roll = create(command);
        roll.authorizeAiExecution();
        return execute(roll);
    }

    private DiceRoll execute(DiceRoll roll) {
        var faces = new ArrayList<Integer>(roll.expression().count());
        for (int index = 0; index < roll.expression().count(); index++) {
            int value = randomPort.nextInt(roll.expression().sides());
            if (value < 0 || value >= roll.expression().sides()) {
                throw new IllegalStateException("random port returned value outside bound");
            }
            faces.add(value + 1);
        }
        roll.recordBuiltInResult(DiceResult.forExpression(roll.expression(), faces));
        repository.save(roll);
        return roll;
    }

    private static DiceRoll create(RollCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        return new DiceRoll(
                RollId.generate(), command.adventureId(), command.ruleSetId(), command.scope(), command.expression());
    }
}
