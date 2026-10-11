package com.dndmaster.adventure.dice.application;

import com.dndmaster.adventure.dice.domain.DiceResult;
import com.dndmaster.adventure.dice.domain.DiceRoll;
import com.dndmaster.adventure.dice.domain.RollId;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DiceRollApplicationService {
    private static final Logger LOGGER = LoggerFactory.getLogger(DiceRollApplicationService.class);
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
        DiceRoll candidate = create(command);
        if (playerExecution) candidate.authorizePlayerExecution(); else candidate.authorizeAiExecution();
        String context = "commandId=" + command.commandId() + " adventureId=" + command.adventureId().value()
                + " scope=" + command.scope() + " diceCount=" + command.expression().count()
                + " dieSides=" + command.expression().sides();
        long startedAt = com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics
                .begin(LOGGER, "dice_roll", context);
        try {
            DiceRoll existing = repository.findByCommandId(command.commandId()).orElse(null);
            if (existing != null) {
                try {
                    requireSame(existing, command);
                } catch (RuntimeException conflict) {
                    com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.fail(LOGGER,
                            "dice_roll_replay", context + " expectedScope=" + command.scope()
                                    + " actualScope=" + existing.scope() + " expectedCount=" + command.expression().count()
                                    + " actualCount=" + existing.expression().count()
                                    + " expectedSides=" + command.expression().sides()
                                    + " actualSides=" + existing.expression().sides() + " result=conflict",
                            startedAt, conflict);
                    throw conflict;
                }
                com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.complete(LOGGER,
                        "dice_roll_replay", context + " branch=saved_result_reused", startedAt);
                return existing;
            }
        } catch (RuntimeException failure) {
            if (!"dice command id reused with different payload".equals(failure.getMessage())) {
                com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.fail(LOGGER,
                        "dice_roll", context + " branch=initial_lookup expected=roll_or_replay actual=lookup_failure",
                        startedAt, failure);
            }
            throw failure;
        }
        try {
            var faces = new ArrayList<Integer>(candidate.expression().count());
            for (int index = 0; index < candidate.expression().count(); index++) {
                int value = randomPort.nextInt(candidate.expression().sides());
                if (value < 0 || value >= candidate.expression().sides()) {
                    throw new IllegalStateException("random port returned value outside bound");
                }
                faces.add(value + 1);
            }
            candidate.recordBuiltInResult(DiceResult.forExpression(candidate.expression(), faces));
            repository.save(candidate);
            com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.complete(LOGGER,
                    "dice_roll", context + " branch=new_result_saved faceCount=" + faces.size(), startedAt);
            return candidate;
        } catch (RuntimeException failure) {
            DiceRoll winner = null;
            try {
                winner = repository.findByCommandId(command.commandId()).orElse(null);
            } catch (RuntimeException lookupFailure) {
                if (lookupFailure != failure) failure.addSuppressed(lookupFailure);
            }
            if (winner != null) {
                try {
                    requireSame(winner, command);
                } catch (RuntimeException conflict) {
                    com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.fail(LOGGER,
                            "dice_roll_replay", context + " branch=concurrent_conflict expectedScope=" + command.scope()
                                    + " actualScope=" + winner.scope() + " expectedCount=" + command.expression().count()
                                    + " actualCount=" + winner.expression().count() + " expectedSides="
                                    + command.expression().sides() + " actualSides=" + winner.expression().sides()
                                    + " result=conflict", startedAt, conflict);
                    throw conflict;
                }
                com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.complete(LOGGER,
                        "dice_roll_replay", context + " branch=concurrent_saved_result_reused faceCount="
                                + winner.result().orElseThrow().faces().size(), startedAt);
                return winner;
            }
            com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.fail(LOGGER,
                    "dice_roll", context + " expected=1_saved_result actual=0 next=retry_same_command", startedAt, failure);
            throw failure;
        }
    }

    private static void requireSame(DiceRoll existing, RollCommand command) {
        if (!existing.adventureId().equals(command.adventureId())
                || !existing.ruleSetId().equals(command.ruleSetId())
                || !existing.scope().equals(command.scope())
                || !existing.expression().equals(command.expression())
                || !Objects.equals(existing.ruleReference(), command.ruleReference())
                || !Objects.equals(existing.difficulty(), command.difficulty())
                || !existing.sessionId().equals(command.sessionId())
                || !existing.turnId().equals(command.turnId())
                || existing.expectedVersion() != command.expectedVersion()) {
            throw new IllegalStateException("dice command id reused with different payload");
        }
    }

    private static DiceRoll create(RollCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        return new DiceRoll(
                RollId.generate(), command.adventureId(), command.ruleSetId(), command.scope(), command.expression(),
                command.sessionId(), command.turnId(), command.commandId(), command.expectedVersion(),
                command.ruleReference(), command.difficulty());
    }
}
