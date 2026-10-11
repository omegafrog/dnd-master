package com.dndmaster.adventure.dice;

import com.dndmaster.adventure.application.combat.CombatActionCommand;
import com.dndmaster.adventure.application.combat.DiceCombatPort;
import com.dndmaster.adventure.application.combat.EnemyObservationRollCommand;
import com.dndmaster.adventure.application.combat.EnemyObservationRollPort;
import com.dndmaster.adventure.application.combat.SpatialCheckRollCommand;
import com.dndmaster.adventure.application.runtime.GmToolInvocation;
import com.dndmaster.adventure.application.runtime.GmToolOutcome;
import com.dndmaster.adventure.application.runtime.GmToolExecutionContext;
import com.dndmaster.adventure.application.runtime.OfficialToolPort;
import com.dndmaster.adventure.application.runtime.TypedCheckRule;
import com.dndmaster.adventure.dice.application.DiceRollApplicationService;
import com.dndmaster.adventure.dice.application.RollCommand;
import com.dndmaster.adventure.dice.domain.AdventureId;
import com.dndmaster.adventure.dice.domain.DiceExpression;
import com.dndmaster.adventure.dice.domain.DiceRoll;
import com.dndmaster.adventure.dice.domain.RollScope;
import com.dndmaster.adventure.dice.domain.RuleSetId;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class AdventureDicePorts implements DiceCombatPort, EnemyObservationRollPort, OfficialToolPort {
    private final DiceRollApplicationService rolls;
    private final ObjectMapper mapper;

    public AdventureDicePorts(DiceRollApplicationService rolls, ObjectMapper mapper) {
        this.rolls = Objects.requireNonNull(rolls);
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public int roll(CombatActionCommand command) {
        RollScope scope = command.role() == com.dndmaster.adventure.application.combat.CombatActorRole.PLAYER
                ? RollScope.PLAYER_ACTION : command.role() == com.dndmaster.adventure.application.combat.CombatActorRole.ENEMY
                ? RollScope.ENEMY : RollScope.NPC;
        return execute(command.adventureId().value(), command.ruleSetId().value(), scope, 1, 20, 0,
                command.sessionId(), command.operationId(), stageId(command.operationId(), "roll"),
                command.expectedVersion(), null, null);
    }

    public int rollInitiative(UUID adventureId, UUID ruleSetId, UUID sessionId, UUID preparationCommandId,
            UUID participantId, long version, RollScope scope) {
        UUID commandId = stageId(preparationCommandId, "initiative|" + participantId);
        return execute(adventureId, ruleSetId, scope,
                1, 20, 0, sessionId, preparationCommandId, commandId, version, null, null);
    }

    @Override
    public int rollDamage(int count, int sides, int modifier) {
        throw new IllegalStateException("damage rolls require a combat command id");
    }

    @Override
    public int rollDamage(CombatActionCommand command, int count, int sides, int modifier) {
        return execute(command.adventureId().value(), command.ruleSetId().value(), scope(command), count, sides,
                modifier, command.sessionId(), command.operationId(), stageId(command.operationId(), "damage"),
                command.expectedVersion(), null, null);
    }

    @Override
    public int rollSpatialCheck(SpatialCheckRollCommand command) {
        TypedCheckRule.DiceExpression expression = TypedCheckRule.DiceExpression.parse(
                command.diceExpression(), command.modifier());
        RollScope scope = command.actor() == com.dndmaster.adventure.application.combat.CombatMapCheckActor.ENEMY
                ? RollScope.ENEMY : RollScope.PLAYER_ACTION;
        return execute(command.adventureId(), command.ruleSetId().value(), scope,
                expression.count(), expression.sides(), expression.modifier(), command.sessionId(),
                command.operationId(), command.commandId(), command.expectedVersion(), command.ruleReference(),
                command.difficulty());
    }

    @Override
    public int rollEnemyObservation(EnemyObservationRollCommand command) {
        TypedCheckRule.DiceExpression expression = TypedCheckRule.DiceExpression.parse(
                command.diceExpression(), command.modifier());
        return execute(command.adventureId(), command.ruleSetId().value(), RollScope.ENEMY,
                expression.count(), expression.sides(), expression.modifier(), command.sessionId(), command.turnId(),
                command.commandId(), command.expectedVersion(), command.ruleReference(), command.difficulty());
    }

    @Override
    public GmToolOutcome execute(GmToolInvocation invocation) {
        GmToolExecutionContext context = invocation.executionContext();
        if (context == null) return GmToolOutcome.rejected("dice tool execution context missing");
        try {
            Map<String, Object> intent = mapper.readValue(invocation.argumentsJson(),
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
            RollScope scope = RollScope.valueOf(Objects.toString(intent.get("scope")));
            DiceRoll roll = rolls.executeAiRoll(new RollCommand(new AdventureId(context.adventureId()),
                    new RuleSetId(context.ruleSetId()), scope,
                    new DiceExpression(number(intent, "count"), number(intent, "sides"), number(intent, "modifier")),
                    invocation.sessionId(), invocation.turnId(), invocation.invocationId(), context.adventureVersion(),
                    null, null));
            return GmToolOutcome.completed(mapper.writeValueAsString(com.dndmaster.adventure.dice.api.DiceRollResponse.from(roll)));
        } catch (RuntimeException failure) {
            return GmToolOutcome.rejected("dice command rejected: " + failure.getClass().getSimpleName());
        } catch (Exception failure) {
            return GmToolOutcome.rejected("dice command could not be processed");
        }
    }

    @Override
    public Optional<GmToolOutcome> query(UUID commandId) {
        return rolls.findByCommandId(commandId).map(roll -> {
            try {
                return GmToolOutcome.completed(mapper.writeValueAsString(
                        com.dndmaster.adventure.dice.api.DiceRollResponse.from(roll)));
            } catch (Exception failure) {
                throw new IllegalStateException("saved dice result could not be serialized", failure);
            }
        });
    }

    private int execute(UUID adventureId, UUID ruleSetId, RollScope scope, int count, int sides, int modifier,
            UUID sessionId, UUID turnId, UUID commandId, long version, String reference, Integer difficulty) {
        DiceRoll roll = scope == RollScope.PLAYER_ACTION
                ? rolls.executePlayerRoll(new RollCommand(new AdventureId(adventureId), new RuleSetId(ruleSetId), scope,
                        new DiceExpression(count, sides, modifier), sessionId, turnId, commandId, version, reference, difficulty))
                : rolls.executeAiRoll(new RollCommand(new AdventureId(adventureId), new RuleSetId(ruleSetId), scope,
                        new DiceExpression(count, sides, modifier), sessionId, turnId, commandId, version, reference, difficulty));
        return roll.result().orElseThrow().total();
    }

    private static RollScope scope(CombatActionCommand command) {
        return command.role() == com.dndmaster.adventure.application.combat.CombatActorRole.PLAYER
                ? RollScope.PLAYER_ACTION : command.role() == com.dndmaster.adventure.application.combat.CombatActorRole.ENEMY
                ? RollScope.ENEMY : RollScope.NPC;
    }

    private static int number(Map<String, Object> values, String key) {
        Object value = values.get(key);
        if (!(value instanceof Number number)) throw new IllegalArgumentException("dice " + key + " must be an integer");
        return number.intValue();
    }

    private static UUID stageId(UUID operationId, String stage) {
        return UUID.nameUUIDFromBytes((operationId + "|" + stage).getBytes(StandardCharsets.UTF_8));
    }
}
