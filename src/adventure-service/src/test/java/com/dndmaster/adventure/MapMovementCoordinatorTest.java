package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.adventure.application.combat.CombatMapCheckActor;
import com.dndmaster.adventure.application.combat.CombatMapCheckDetails;
import com.dndmaster.adventure.application.combat.CombatMapMoveResult;
import com.dndmaster.adventure.application.combat.CombatMapMovementStatus;
import com.dndmaster.adventure.application.combat.CombatMapPendingCheck;
import com.dndmaster.adventure.application.combat.CombatMapPort;
import com.dndmaster.adventure.application.combat.MapMovementCoordinator;
import com.dndmaster.adventure.application.combat.CombatMapSpatialActionCommand;
import com.dndmaster.adventure.application.combat.CombatMapSpatialResult;
import com.dndmaster.adventure.application.combat.SpatialActionAuthorizationPort;
import com.dndmaster.adventure.application.combat.SpatialCheckRollCommand;
import com.dndmaster.adventure.domain.adventure.RuleSetId;
import com.dndmaster.adventure.domain.combat.TurnResourceCost;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class MapMovementCoordinatorTest {
    @Test
    void rolls_and_resumes_only_after_matching_check_and_owner_are_verified() {
        UUID mapId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        UUID checkId = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        CapturingMapPort map = new CapturingMapPort(mapId, operationId, checkId, owner, 15);
        MapMovementCoordinator coordinator = new MapMovementCoordinator(map);

        coordinator.rollAndResume(new SpatialCheckRollCommand(UUID.randomUUID(), mapId, UUID.randomUUID(),
                new RuleSetId(UUID.randomUUID()), owner, checkId, operationId, 3));

        assertTrue(map.submission.success());
        assertEquals(checkId, map.submission.checkId());
        assertEquals(owner, map.submission.ownerPlayerId());
    }

    @Test
    void rejects_a_roll_for_a_different_owner_before_calling_the_dice_gateway() {
        UUID mapId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        UUID checkId = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        CapturingMapPort map = new CapturingMapPort(mapId, operationId, checkId, owner, 15);

        assertThrows(IllegalArgumentException.class, () -> new MapMovementCoordinator(map).rollAndResume(
                new SpatialCheckRollCommand(UUID.randomUUID(), mapId, UUID.randomUUID(), new RuleSetId(UUID.randomUUID()),
                        UUID.randomUUID(), checkId, operationId, 3)));
        assertEquals(0, map.rolls);
    }

    @Test
    void routes_spatial_actions_through_typed_action_and_cost_authorization() {
        UUID owner = UUID.randomUUID();
        UUID commandId = UUID.randomUUID();
        CapturingMapPort map = new CapturingMapPort(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), owner, 15);
        AtomicReference<SpatialActionAuthorizationPort.SpatialActionAuthorization> authorized = new AtomicReference<>();
        MapMovementCoordinator coordinator = new MapMovementCoordinator(map, command -> 1,
                new com.dndmaster.adventure.application.runtime.DefaultResolutionPort(), authorized::set);
        CombatMapSpatialActionCommand command = new CombatMapSpatialActionCommand(map.mapId, owner, UUID.randomUUID(),
                new com.dndmaster.adventure.application.combat.CombatMapPreviewPosition(1, 1), 0, commandId);

        coordinator.observe(command);
        assertEquals("OBSERVE", authorized.get().action());
        assertEquals(owner, authorized.get().actorId());
        assertEquals(TurnResourceCost.actionOnly(), authorized.get().cost());
        assertEquals(1, map.observations);

        coordinator.interact(command);
        assertEquals("INTERACT", authorized.get().action());
        assertEquals(1, map.interactions);
    }

    @Test
    void rejects_spatial_actions_before_the_map_port_when_runtime_authorization_fails() {
        UUID owner = UUID.randomUUID();
        CapturingMapPort map = new CapturingMapPort(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), owner, 15);
        MapMovementCoordinator coordinator = new MapMovementCoordinator(map, command -> 1,
                new com.dndmaster.adventure.application.runtime.DefaultResolutionPort(), command -> {
                    throw new IllegalStateException("action resource is unavailable");
                });
        CombatMapSpatialActionCommand command = new CombatMapSpatialActionCommand(map.mapId, owner, UUID.randomUUID(),
                new com.dndmaster.adventure.application.combat.CombatMapPreviewPosition(1, 1), 0, UUID.randomUUID());

        assertThrows(IllegalStateException.class, () -> coordinator.observe(command));
        assertEquals(0, map.observations);
    }

    private static final class CapturingMapPort implements CombatMapPort {
        private final UUID mapId;
        private final CombatMapMoveResult pending;
        private final int roll;
        private int observations;
        private int interactions;
        private int rolls;
        private com.dndmaster.adventure.application.combat.CombatMapCheckSubmission submission;

        private CapturingMapPort(UUID mapId, UUID operationId, UUID checkId, UUID owner, int roll) {
            this.mapId = mapId;
            this.roll = roll;
            CombatMapPendingCheck safe = new CombatMapPendingCheck(checkId, operationId, "지각 판정", "d20", owner,
                    CombatMapCheckActor.PLAYER);
            CombatMapCheckDetails details = new CombatMapCheckDetails(checkId, operationId, "perception", 15, owner,
                    CombatMapCheckActor.PLAYER);
            this.pending = new CombatMapMoveResult(0, operationId, CombatMapMovementStatus.CHECK_REQUIRED,
                    List.of(), List.of(), null, List.of(), null, safe, details);
        }

        @Override public void validateAndMove(com.dndmaster.adventure.application.combat.CombatActionCommand command) {}
        @Override public CombatMapMoveResult movementOperation(UUID mapId, UUID operationId) {
            if (!this.mapId.equals(mapId)) throw new IllegalArgumentException("wrong map");
            return pending;
        }
        @Override public int rollSpatialCheck(SpatialCheckRollCommand command) { rolls++; return roll; }
        @Override public CombatMapSpatialResult observe(CombatMapSpatialActionCommand command) {
            observations++;
            return new CombatMapSpatialResult(mapId, 1, List.of());
        }
        @Override public CombatMapSpatialResult interact(CombatMapSpatialActionCommand command) {
            interactions++;
            return new CombatMapSpatialResult(mapId, 1, List.of());
        }
        @Override public CombatMapMoveResult resumeMovementOperation(UUID mapId, UUID operationId,
                com.dndmaster.adventure.application.combat.CombatMapCheckSubmission submission) {
            this.submission = submission;
            return pending;
        }
    }
}
