package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndmaster.adventure.application.combat.CombatActionOperation;
import com.dndmaster.adventure.application.combat.CombatActionOperationRepository;
import com.dndmaster.adventure.application.combat.CombatEncounterRepository;
import com.dndmaster.adventure.application.combat.CombatEncounterSpatialActionAuthorization;
import com.dndmaster.adventure.application.combat.SpatialActionAuthorizationPort;
import com.dndmaster.adventure.domain.combat.CombatEncounter;
import com.dndmaster.adventure.domain.combat.CombatParticipant;
import com.dndmaster.adventure.domain.combat.TurnResourceCost;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CombatEncounterSpatialActionAuthorizationTest {
    @Test
    void verifies_current_actor_and_consumes_action_once_with_command_replay() {
        UUID adventureId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        EncounterStore encounters = new EncounterStore(activeEncounter(adventureId, actorId));
        OperationStore operations = new OperationStore();
        CombatEncounterSpatialActionAuthorization authorization = new CombatEncounterSpatialActionAuthorization(encounters, operations);
        SpatialActionAuthorizationPort.SpatialActionAuthorization command = command(adventureId, actorId, UUID.randomUUID(), "observe");

        authorization.authorize(command);

        assertFalse(encounters.current.currentParticipant().resources().actionAvailable());
        assertEquals(CombatActionOperation.Status.COMMITTED, operations.values.get(command.commandId()).status());
        authorization.authorize(command);
        assertFalse(encounters.current.currentParticipant().resources().actionAvailable());
    }

    @Test
    void rejects_a_non_current_actor_before_persisting_an_action() {
        UUID adventureId = UUID.randomUUID();
        UUID currentActor = UUID.randomUUID();
        EncounterStore encounters = new EncounterStore(activeEncounter(adventureId, currentActor));
        OperationStore operations = new OperationStore();
        CombatEncounterSpatialActionAuthorization authorization = new CombatEncounterSpatialActionAuthorization(encounters, operations);

        assertThrows(IllegalStateException.class,
                () -> authorization.authorize(command(adventureId, UUID.randomUUID(), UUID.randomUUID(), "INTERACT")));
        assertEquals(0, operations.values.size());
    }

    @Test
    void rejects_spatial_actions_outside_an_active_player_turn() {
        UUID adventureId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        for (CombatEncounter.Status status : List.of(CombatEncounter.Status.PREPARING, CombatEncounter.Status.ENDED)) {
            EncounterStore encounters = new EncounterStore(new CombatEncounter(UUID.randomUUID(), adventureId, status, 1,
                    actorId, List.of(new CombatParticipant(actorId, "영웅", CombatParticipant.Controller.PLAYER, 10, "")), 1, 0));
            OperationStore operations = new OperationStore();
            CombatEncounterSpatialActionAuthorization authorization = new CombatEncounterSpatialActionAuthorization(encounters, operations);

            assertThrows(IllegalStateException.class,
                    () -> authorization.authorize(command(adventureId, actorId, UUID.randomUUID(), "OBSERVE")));
            assertEquals(0, operations.values.size());
        }
    }

    @Test
    void rejects_a_second_operation_after_the_action_resource_was_consumed() {
        UUID adventureId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        EncounterStore encounters = new EncounterStore(activeEncounter(adventureId, actorId));
        OperationStore operations = new OperationStore();
        CombatEncounterSpatialActionAuthorization authorization = new CombatEncounterSpatialActionAuthorization(encounters, operations);

        authorization.authorize(command(adventureId, actorId, UUID.randomUUID(), "OBSERVE"));

        assertThrows(IllegalStateException.class,
                () -> authorization.authorize(command(adventureId, actorId, UUID.randomUUID(), "INTERACT")));
    }

    private static SpatialActionAuthorizationPort.SpatialActionAuthorization command(UUID adventureId, UUID actorId,
            UUID commandId, String action) {
        return new SpatialActionAuthorizationPort.SpatialActionAuthorization(adventureId, actorId, action,
                TurnResourceCost.actionOnly(), commandId, adventureId + "|fingerprint");
    }

    private static CombatEncounter activeEncounter(UUID adventureId, UUID actorId) {
        return new CombatEncounter(UUID.randomUUID(), adventureId, CombatEncounter.Status.ACTIVE, 1, actorId,
                List.of(new CombatParticipant(actorId, "영웅", CombatParticipant.Controller.PLAYER, 10, "")), 1, 0);
    }

    private static final class EncounterStore implements CombatEncounterRepository {
        private CombatEncounter current;

        private EncounterStore(CombatEncounter current) { this.current = current; }
        @Override public Optional<CombatEncounter> findActive(UUID adventureId) {
            return current.adventureId().equals(adventureId) ? Optional.of(current) : Optional.empty();
        }
        @Override public CombatEncounter save(CombatEncounter encounter) {
            current = encounter;
            return encounter;
        }
        @Override public CombatEncounter save(CombatEncounter encounter, long expectedVersion) {
            if (current.version() != expectedVersion) throw new IllegalStateException("COMBAT_VERSION_CONFLICT");
            current = encounter;
            return encounter;
        }
    }

    private static final class OperationStore implements CombatActionOperationRepository {
        private final Map<UUID, CombatActionOperation> values = new HashMap<>();
        @Override public Optional<CombatActionOperation> findByCommandId(UUID commandId) {
            return Optional.ofNullable(values.get(commandId));
        }
        @Override public void save(CombatActionOperation operation) { values.put(operation.commandId(), operation); }
    }
}
