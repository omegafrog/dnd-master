package com.dndmaster.adventure.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.adventure.application.combat.*;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.CharacterSheetId;
import com.dndmaster.adventure.domain.adventure.RuleSetId;
import com.dndmaster.adventure.domain.combat.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class BattlefieldModePolicyTest {
    @Test
    void mapless_movement_never_calls_the_map_boundary() {
        UUID adventureId = UUID.randomUUID();
        UUID heroId = UUID.randomUUID();
        UUID enemyId = UUID.randomUUID();
        EncounterStore encounters = new EncounterStore(CombatStartPolicy.startFromCommittedGmTurn(true, adventureId, List.of(
                new CombatParticipant(heroId, "Hero", CombatParticipant.Controller.PLAYER, 15, "healthy"),
                new CombatParticipant(enemyId, "Goblin", CombatParticipant.Controller.AI, 10, null))));
        CountingMapPort map = new CountingMapPort();
        CombatActionApplicationService service = new CombatActionApplicationService(encounters, new OperationStore(),
                new EventStore(), new CombatRulesEngine(), ignored -> 1, new CharacterCombatPort() {
                    @Override public void requireUsableCharacter(CombatActionCommand ignored) {}
                }, new AiCombatPort() {
                    @Override public void controlState(CombatActionCommand ignored) {}
                    @Override public String adjudicate(CombatActionCommand ignored, int diceTotal) { return "ok"; }
                }, map);

        service.submit(new CombatActionCommand(UUID.randomUUID(), new AdventureId(adventureId),
                UUID.randomUUID(), new RuleSetId(UUID.randomUUID()), new CharacterSheetId(heroId), null,
                CombatActorRole.PLAYER, "MOVE", null, UUID.randomUUID(), heroId, 1,
                null, null, null, null, false, new NarrativeCombatPosition(heroId, enemyId, "NEAR", "NONE"), 10));

        assertEquals(0, map.calls);
        assertEquals(20, encounters.value.currentParticipant().resources().movement());
        assertFalse(encounters.value.narrativePositions().isEmpty());
    }

    @Test
    void mapped_movement_uses_the_dedicated_boundary_and_is_idempotent() {
        UUID adventureId = UUID.randomUUID();
        UUID heroId = UUID.randomUUID();
        EncounterStore encounters = encounters(adventureId, heroId);
        CountingMapPort map = new CountingMapPort();
        OperationStore operations = new OperationStore();
        CombatActionApplicationService service = service(encounters, operations, map);
        UUID commandId = UUID.randomUUID();
        CombatActionCommand command = new CombatActionCommand(commandId, new AdventureId(adventureId),
                UUID.randomUUID(), new RuleSetId(UUID.randomUUID()), new CharacterSheetId(heroId), UUID.randomUUID(),
                CombatActorRole.PLAYER, "MOVE", "0,0;1,0;2,0", UUID.randomUUID(), UUID.randomUUID(), 1,
                null, null, null, null, false);

        var first = service.submit(command);
        var retry = service.submit(command);

        assertEquals(first, retry);
        assertEquals(1, map.calls);
        assertEquals(20, encounters.value.currentParticipant().resources().movement());
        assertTrue(operations.values.get(commandId).steps().stream().allMatch(step -> step.status() == CombatActionStep.Status.DONE));
    }

    @Test
    void mapped_movement_forwards_map_version_separately_from_encounter_version() {
        UUID adventureId = UUID.randomUUID();
        UUID heroId = UUID.randomUUID();
        EncounterStore encounters = encounters(adventureId, heroId);
        CountingMapPort map = new CountingMapPort();
        CombatActionApplicationService service = service(encounters, new OperationStore(), map);
        CombatActionCommand command = new CombatActionCommand(UUID.randomUUID(), new AdventureId(adventureId),
                UUID.randomUUID(), new RuleSetId(UUID.randomUUID()), new CharacterSheetId(heroId), UUID.randomUUID(),
                CombatActorRole.PLAYER, "MOVE", "0,0;1,0", UUID.randomUUID(), UUID.randomUUID(), 1,
                null, null, null, null, false, null, null, 17L);

        service.submit(command);

        assertEquals(17L, map.received.expectedVersion());
    }

    @Test
    void rejected_mapped_destination_does_not_consume_movement_or_advance_encounter() {
        UUID adventureId = UUID.randomUUID();
        UUID heroId = UUID.randomUUID();
        EncounterStore encounters = encounters(adventureId, heroId);
        CountingMapPort map = new CountingMapPort();
        map.reject = true;
        CombatActionApplicationService service = service(encounters, new OperationStore(), map);
        CombatActionCommand command = new CombatActionCommand(UUID.randomUUID(), new AdventureId(adventureId),
                UUID.randomUUID(), new RuleSetId(UUID.randomUUID()), new CharacterSheetId(heroId), UUID.randomUUID(),
                CombatActorRole.PLAYER, "MOVE", "0,0;1,0", UUID.randomUUID(), UUID.randomUUID(), 1,
                null, null, null, null, false);

        assertThrows(RuntimeException.class, () -> service.submit(command));
        assertEquals(1, encounters.value.version());
        assertEquals(30, encounters.value.currentParticipant().resources().movement());
    }

    @Test
    void mapless_position_must_reference_an_encounter_participant() {
        UUID adventureId = UUID.randomUUID();
        UUID heroId = UUID.randomUUID();
        EncounterStore encounters = encounters(adventureId, heroId);
        CombatActionApplicationService service = service(encounters, new OperationStore(), new CountingMapPort());
        CombatActionCommand command = new CombatActionCommand(UUID.randomUUID(), new AdventureId(adventureId),
                UUID.randomUUID(), new RuleSetId(UUID.randomUUID()), new CharacterSheetId(heroId), null,
                CombatActorRole.PLAYER, "MOVE", null, UUID.randomUUID(), heroId, 1,
                null, null, null, null, false,
                new NarrativeCombatPosition(heroId, UUID.randomUUID(), "NEAR", "NONE"), 5, null);

        assertThrows(RuntimeException.class, () -> service.submit(command));
        assertEquals(1, encounters.value.version());
        assertEquals(30, encounters.value.currentParticipant().resources().movement());
    }

    private static CombatActionApplicationService service(EncounterStore encounters, OperationStore operations,
                                                           CountingMapPort map) {
        return new CombatActionApplicationService(encounters, operations, new EventStore(), new CombatRulesEngine(),
                ignored -> 1, new CharacterCombatPort() {
                    @Override public void requireUsableCharacter(CombatActionCommand ignored) {}
                }, new AiCombatPort() {
                    @Override public void controlState(CombatActionCommand ignored) {}
                    @Override public String adjudicate(CombatActionCommand ignored, int diceTotal) { return "ok"; }
                }, map);
    }

    private static EncounterStore encounters(UUID adventureId, UUID heroId) {
        return new EncounterStore(CombatStartPolicy.startFromCommittedGmTurn(true, adventureId, List.of(
                new CombatParticipant(heroId, "Hero", CombatParticipant.Controller.PLAYER, 15, "healthy"),
                new CombatParticipant(UUID.randomUUID(), "Goblin", CombatParticipant.Controller.AI, 10, null))));
    }

    private static final class CountingMapPort implements CombatMapPort {
        int calls;
        boolean reject;
        CombatMapMoveCommand received;
        @Override public void validateAndMove(CombatActionCommand command) {
            calls++;
            if (reject) throw new IllegalStateException("destination blocked");
        }
        @Override public CombatMapMoveResult move(CombatMapMoveCommand command) {
            received = command;
            calls++;
            if (reject) throw new IllegalStateException("destination blocked");
            return new CombatMapMoveResult(command.expectedVersion() + 1);
        }
    }
    private static final class EncounterStore implements CombatEncounterRepository {
        CombatEncounter value;
        EncounterStore(CombatEncounter value) { this.value = value; }
        @Override public Optional<CombatEncounter> findActive(UUID ignored) { return Optional.of(value); }
        @Override public CombatEncounter save(CombatEncounter encounter) { value = encounter; return encounter; }
    }
    private static final class OperationStore implements CombatActionOperationRepository {
        final Map<UUID, CombatActionOperation> values = new HashMap<>();
        @Override public Optional<CombatActionOperation> findByCommandId(UUID id) { return Optional.ofNullable(values.get(id)); }
        @Override public void save(CombatActionOperation operation) { values.put(operation.commandId(), operation); }
    }
    private static final class EventStore implements CombatEventRepository {
        @Override public void append(CombatEvent event) {}
        @Override public List<CombatEvent> after(UUID ignored, long sequence) { return List.of(); }
    }
}
