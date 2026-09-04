package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.application.combat.CombatActorRole;
import com.dndmaster.adventure.application.combat.CombatActionCommand;
import com.dndmaster.adventure.application.combat.CombatMapPort;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.CharacterSheetId;
import com.dndmaster.adventure.domain.adventure.RuleSetId;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;

/** Adapts the durable map move command to Combat Map's authoritative command API. */
public final class CombatMapRuntimeTurnCommandAdapter implements RuntimeTurnCommandAdapter {
    private final CombatMapPort mapPort;
    private final ObjectMapper mapper;

    public CombatMapRuntimeTurnCommandAdapter(CombatMapPort mapPort, ObjectMapper mapper) {
        this.mapPort = java.util.Objects.requireNonNull(mapPort, "combat map port must not be null");
        this.mapper = java.util.Objects.requireNonNull(mapper, "object mapper must not be null");
    }

    @Override public RuntimeTurnCommandExecution execute(RuntimeTurnCommand command) {
        try {
            var payload = mapper.readTree(command.payloadJson());
            var context = mapper.readTree(command.targetContext());
            String path = java.util.stream.StreamSupport.stream(payload.path("path").spliterator(), false)
                    .map(position -> position.path("x").asInt() + "," + position.path("y").asInt())
                    .reduce((left, right) -> left + ";" + right).orElseThrow();
            CombatActionCommand mapCommand = new CombatActionCommand(command.commandId(),
                    new AdventureId(command.adventureId()), command.sessionId(),
                    new RuleSetId(UUID.fromString(context.path("ruleSetId").asText())),
                    new CharacterSheetId(UUID.fromString(context.path("characterSheetId").asText())),
                    UUID.fromString(context.path("combatMapId").asText()), CombatActorRole.PLAYER,
                    payload.path("action").asText(), path, command.ownerPlayerId(),
                    UUID.fromString(context.path("tokenId").asText()), context.path("expectedVersion").asLong());
            mapPort.validateAndMove(mapCommand);
            return RuntimeTurnCommandExecution.done("combat map move applied");
        } catch (java.io.IOException malformed) {
            return RuntimeTurnCommandExecution.permanentFailure(malformed.getMessage());
        } catch (IllegalArgumentException malformed) {
            return RuntimeTurnCommandExecution.permanentFailure(malformed.getMessage());
        } catch (RuntimeException transientFailure) {
            return RuntimeTurnCommandExecution.transientFailure(transientFailure.getMessage());
        }
    }
}
