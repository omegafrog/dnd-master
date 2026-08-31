package com.dndmaster.diceroll.application;

import com.dndmaster.diceroll.domain.DiceResult;
import com.dndmaster.diceroll.domain.DiceRoll;
import com.dndmaster.diceroll.domain.RollId;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;

public final class DiceRollApplicationService {
    private final DiceRollRepository repository;
    private final DiceRandomPort randomPort;

    public DiceRollApplicationService(DiceRollRepository repository, DiceRandomPort randomPort) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.randomPort = Objects.requireNonNull(randomPort, "random port must not be null");
    }

    public DiceRoll executePlayerRoll(RollCommand command) {
        return execute(command, true);
    }

    public DiceRoll executeAiRoll(RollCommand command) {
        return execute(command, false);
    }

    public Optional<DiceRoll> findByCommandId(java.util.UUID commandId) { return repository.findByCommandId(commandId); }

    private DiceRoll execute(RollCommand command, boolean playerExecution) {
        Objects.requireNonNull(command, "command must not be null");
        DiceRoll existing = repository.findByCommandId(command.commandId()).orElse(null);
        if (existing != null) {
            if (!existing.adventureId().equals(command.adventureId())
                    || !existing.ruleSetId().equals(command.ruleSetId())
                    || !existing.scope().equals(command.scope())
                    || !existing.expression().equals(command.expression())
                    || !existing.sessionId().equals(command.sessionId())
                    || !existing.turnId().equals(command.turnId())
                    || existing.expectedVersion() != command.expectedVersion()) {
                throw new IllegalStateException("dice command id reused with different payload");
            }
            return existing;
        }
        DiceRoll roll = create(command);
        if (playerExecution) {
            roll.authorizePlayerExecution();
        } else {
            roll.authorizeAiExecution();
        }
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
                RollId.generate(), command.adventureId(), command.ruleSetId(), command.scope(), command.expression(),
                command.sessionId(), command.turnId(), command.commandId(), command.expectedVersion());
    }
}
