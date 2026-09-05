package com.dndmaster.adventure.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndmaster.adventure.application.combat.AiCombatPort;
import com.dndmaster.adventure.application.combat.CharacterCombatPort;
import com.dndmaster.adventure.application.combat.CombatActionCommand;
import com.dndmaster.adventure.application.combat.CombatActionApplicationService;
import com.dndmaster.adventure.application.combat.CombatActionOperation;
import com.dndmaster.adventure.application.combat.CombatActionOperationRepository;
import com.dndmaster.adventure.application.combat.CombatActionResponse;
import com.dndmaster.adventure.application.combat.CombatActorRole;
import com.dndmaster.adventure.application.combat.CombatEncounterRepository;
import com.dndmaster.adventure.application.combat.CombatEventRepository;
import com.dndmaster.adventure.application.combat.DiceCombatPort;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.CharacterSheetId;
import com.dndmaster.adventure.domain.adventure.RuleSetId;
import com.dndmaster.adventure.domain.combat.CombatEncounter;
import com.dndmaster.adventure.domain.combat.CombatEvent;
import com.dndmaster.adventure.domain.combat.CombatParticipant;
import com.dndmaster.adventure.domain.combat.CombatStartPolicy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CombatActionPolicyTest {
    private final UUID adventureId = UUID.randomUUID();
    private final UUID heroId = UUID.randomUUID();
    private final UUID goblinId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();
    private final UUID ruleSetId = UUID.randomUUID();
    private final UUID sessionId = UUID.randomUUID();

    @Test
    void rejects_non_current_actor_or_version_without_mutation() {
        Fixture fixture = fixture(command(UUID.randomUUID(), goblinId, 1));

        assertThrows(RuntimeException.class, () -> fixture.service.submit(fixture.command));
        assertEquals(1, fixture.encounters.value.version());
        assertEquals(0, fixture.operations.values.size());
        assertEquals(0, fixture.events.values.size());
        assertEquals(0, fixture.calls.dice);
        assertEquals(0, fixture.calls.character);
    }

    @Test
    void external_failure_does_not_consume_reserved_action() {
        CombatActionCommand command = command(UUID.randomUUID(), heroId, 1);
        Fixture fixture = fixture(command);
        fixture.calls.failCharacter = true;

        assertThrows(RuntimeException.class, () -> fixture.service.submit(command));
        assertEquals(true, fixture.encounters.value.participants().get(0).resources().actionAvailable());
        assertEquals(0, fixture.calls.characterMutations);
        assertEquals(1, fixture.operations.values.size());
        assertEquals(CombatActionOperation.Status.PROCESSING_FAILED,
                fixture.operations.values.get(command.operationId()).status());
    }

    @Test
    void retry_resumes_completed_dice_step_without_rerolling_or_double_mutating() {
        CombatActionCommand command = command(UUID.randomUUID(), heroId, 1);
        Fixture fixture = fixture(command);
        fixture.calls.failCharacter = true;
        assertThrows(RuntimeException.class, () -> fixture.service.submit(command));

        fixture.calls.failCharacter = false;
        fixture.service.submit(command);

        assertEquals(1, fixture.calls.dice);
        assertEquals(1, fixture.calls.characterMutations);
    }

    @Test
    void full_success_is_idempotent_and_keeps_human_turn_open() {
        CombatActionCommand command = command(UUID.randomUUID(), heroId, 1);
        Fixture fixture = fixture(command);

        CombatActionResponse first = fixture.service.submit(command);
        CombatActionResponse retry = fixture.service.submit(command);

        assertEquals(first, retry);
        assertEquals(1, fixture.calls.dice);
        assertEquals(1, fixture.calls.characterMutations);
        assertEquals(false, fixture.encounters.value.participants().get(0).resources().actionAvailable());
        assertEquals(heroId, fixture.encounters.value.currentParticipantId());
        assertEquals(1, fixture.events.values.stream().filter(e -> e.eventType().equals("ACTION_RESOLVED")).count());
    }

    @Test
    void human_turn_moves_only_after_explicit_end_turn() {
        CombatActionCommand action = command(UUID.randomUUID(), heroId, 1);
        Fixture fixture = fixture(action);
        fixture.service.submit(action);

        fixture.service.endTurn(command(UUID.randomUUID(), heroId, 2));

        assertEquals(goblinId, fixture.encounters.value.currentParticipantId());
        assertEquals(3, fixture.encounters.value.version());
    }

    private CombatActionCommand command(UUID operationId, UUID actorId, long expectedVersion) {
        return new CombatActionCommand(operationId, new AdventureId(adventureId), sessionId,
                new RuleSetId(ruleSetId), new CharacterSheetId(actorId), null,
                CombatActorRole.PLAYER, "attack", null, ownerId, actorId, expectedVersion,
                null, null, null, null, false);
    }

    private Fixture fixture(CombatActionCommand command) {
        EncounterStore encounters = new EncounterStore(CombatStartPolicy.startFromCommittedGmTurn(true,
                adventureId, List.of(
                        new CombatParticipant(heroId, "Hero", CombatParticipant.Controller.PLAYER, 15, "healthy"),
                        new CombatParticipant(goblinId, "Goblin", CombatParticipant.Controller.AI, 10, null))));
        OperationStore operations = new OperationStore();
        EventStore events = new EventStore();
        Calls calls = new Calls();
        DiceCombatPort dice = ignored -> { calls.dice++; return 18; };
        CharacterCombatPort character = new CharacterCombatPort() {
            @Override public void requireUsableCharacter(CombatActionCommand ignored) { calls.character++; }
            @Override public void applyOutcome(CombatActionCommand ignored, com.dndmaster.adventure.application.combat.CombatOutcome ignoredOutcome) {
                if (calls.failCharacter) throw new IllegalStateException("character unavailable");
                calls.characterMutations++;
            }
        };
        AiCombatPort ai = new AiCombatPort() {
            @Override public void controlState(CombatActionCommand ignored) {}
            @Override public String adjudicate(CombatActionCommand ignored, int ignoredDice) { return "hit"; }
        };
        return new Fixture(new CombatActionApplicationService(encounters, operations, events,
                new com.dndmaster.adventure.domain.combat.CombatRulesEngine(), dice, character, ai),
                encounters, operations, events, calls, command);
    }

    private record Fixture(CombatActionApplicationService service, EncounterStore encounters,
                           OperationStore operations, EventStore events, Calls calls,
                           CombatActionCommand command) {}

    private static final class EncounterStore implements CombatEncounterRepository {
        private CombatEncounter value;
        private EncounterStore(CombatEncounter value) { this.value = value; }
        @Override public Optional<CombatEncounter> findActive(UUID ignored) { return Optional.of(value); }
        @Override public CombatEncounter save(CombatEncounter encounter) { value = encounter; return encounter; }
    }

    private static final class OperationStore implements CombatActionOperationRepository {
        private final Map<UUID, CombatActionOperation> values = new HashMap<>();
        @Override public Optional<CombatActionOperation> findByCommandId(UUID id) { return Optional.ofNullable(values.get(id)); }
        @Override public void save(CombatActionOperation operation) { values.put(operation.commandId(), operation); }
    }

    private static final class EventStore implements CombatEventRepository {
        private final List<CombatEvent> values = new ArrayList<>();
        @Override public void append(CombatEvent event) { values.add(event); }
        @Override public List<CombatEvent> after(UUID ignored, long sequence) { return List.of(); }
    }

    private static final class Calls {
        private int dice;
        private int character;
        private int characterMutations;
        private boolean failCharacter;
    }
}
