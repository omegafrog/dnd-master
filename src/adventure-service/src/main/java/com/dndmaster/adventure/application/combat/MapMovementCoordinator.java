package com.dndmaster.adventure.application.combat;

import com.dndmaster.adventure.application.runtime.DefaultResolutionPort;
import com.dndmaster.adventure.application.runtime.ResolutionPort;
import com.dndmaster.adventure.domain.combat.TurnResourceCost;
import java.util.Objects;

/** Shared Adventure-side boundary for confirmed movement from runtime and map actions. */
public final class MapMovementCoordinator {
    private final CombatMapPort combatMap;
    private final DiceCombatPort dice;
    private final EnemyObservationRollPort enemyObservationRoll;
    private final ResolutionPort resolution;
    private final SpatialActionAuthorizationPort spatialAuthorization;

    public MapMovementCoordinator(CombatMapPort combatMap) {
        this(combatMap, new DiceCombatPort() {
            @Override public int roll(CombatActionCommand command) {
                throw new UnsupportedOperationException("combat dice roll is unavailable");
            }
            @Override public int rollSpatialCheck(SpatialCheckRollCommand command) {
                return combatMap.rollSpatialCheck(command);
            }
            @Override public int rollEnemyObservation(EnemyObservationRollCommand command) {
                return combatMap.rollEnemyObservation(command);
            }
        }, new DefaultResolutionPort(), SpatialActionAuthorizationPort.requiredPlayerAction(), combatMap::rollEnemyObservation);
    }

    public MapMovementCoordinator(CombatMapPort combatMap, SpatialActionAuthorizationPort spatialAuthorization) {
        this(combatMap, new DiceCombatPort() {
            @Override public int roll(CombatActionCommand command) {
                throw new UnsupportedOperationException("combat dice roll is unavailable");
            }
            @Override public int rollSpatialCheck(SpatialCheckRollCommand command) {
                return combatMap.rollSpatialCheck(command);
            }
            @Override public int rollEnemyObservation(EnemyObservationRollCommand command) {
                return combatMap.rollEnemyObservation(command);
            }
        }, new DefaultResolutionPort(), spatialAuthorization, combatMap::rollEnemyObservation);
    }

    public MapMovementCoordinator(CombatMapPort combatMap, DiceCombatPort dice) {
        this(combatMap, dice, new DefaultResolutionPort());
    }

    public MapMovementCoordinator(CombatMapPort combatMap, DiceCombatPort dice, ResolutionPort resolution) {
        this(combatMap, dice, resolution, SpatialActionAuthorizationPort.requiredPlayerAction(), dice::rollEnemyObservation);
    }

    public MapMovementCoordinator(CombatMapPort combatMap, DiceCombatPort dice, ResolutionPort resolution,
            SpatialActionAuthorizationPort spatialAuthorization) {
        this(combatMap, dice, resolution, spatialAuthorization, dice::rollEnemyObservation);
    }

    public MapMovementCoordinator(CombatMapPort combatMap, DiceCombatPort dice, ResolutionPort resolution,
            SpatialActionAuthorizationPort spatialAuthorization, EnemyObservationRollPort enemyObservationRoll) {
        this.combatMap = Objects.requireNonNull(combatMap, "combat map port must not be null");
        this.dice = Objects.requireNonNull(dice, "dice port must not be null");
        this.resolution = Objects.requireNonNull(resolution, "resolution port must not be null");
        this.spatialAuthorization = Objects.requireNonNull(spatialAuthorization, "spatial action authorization must not be null");
        this.enemyObservationRoll = Objects.requireNonNull(enemyObservationRoll, "enemy observation roll port must not be null");
    }

    public CombatMapMoveResult resolve(CombatMapMoveCommand command) {
        return combatMap.move(Objects.requireNonNull(command, "map movement command must not be null"));
    }
    public CombatMapMoveResult query(java.util.UUID mapId, java.util.UUID operationId) { return combatMap.movementOperation(mapId, operationId); }
    public CombatMapMoveResult latest(java.util.UUID mapId) { return combatMap.latestMovementOperation(mapId); }
    public CombatMapMoveResult resume(java.util.UUID mapId, java.util.UUID operationId) { return combatMap.resumeMovementOperation(mapId, operationId); }
    public CombatMapMoveResult resume(java.util.UUID mapId, java.util.UUID operationId, CombatMapCheckSubmission submission) {
        Objects.requireNonNull(submission, "check submission must not be null");
        requireOwnedPendingCheck(mapId, operationId, submission);
        return combatMap.resumeMovementOperation(mapId, operationId, submission);
    }

    public CombatMapMoveResult rollAndResume(SpatialCheckRollCommand command) {
        Objects.requireNonNull(command, "spatial check roll command must not be null");
        CombatMapCheckDetails details = requireOwnedPendingCheck(command.mapId(), command.operationId(), command);
        int rollTotal = dice.rollSpatialCheck(command.withRule(details));
        ResolutionPort.PlayerCheckResult result = resolve(details, rollTotal);
        return combatMap.resumeMovementOperation(command.mapId(), command.operationId(),
                new CombatMapCheckSubmission(command.commandId(), command.operationId(), command.checkId(), result.success(),
                        command.ownerPlayerId(), details.actor()));
    }

    public CombatMapMoveResult rollEnemyAndResume(EnemyObservationRollCommand command) {
        Objects.requireNonNull(command, "enemy observation roll command must not be null");
        CombatMapCheckDetails details = requireEnemyPendingCheck(command.mapId(), command.operationId(), command);
        int rollTotal = enemyObservationRoll.rollEnemyObservation(command.withRule(details));
        ResolutionPort.EnemyObservationCheckResult result = resolution.resolveEnemyObservation(
                new ResolutionPort.EnemyObservationCheckRequest(details.ruleReference(), details.diceExpression(),
                        details.modifier(), details.difficulty(), rollTotal));
        return combatMap.resumeMovementOperation(command.mapId(), command.operationId(),
                new CombatMapCheckSubmission(command.commandId(), command.operationId(), command.checkId(), result.success(),
                        command.ownerPlayerId(), CombatMapCheckActor.ENEMY));
    }

    private ResolutionPort.PlayerCheckResult resolve(CombatMapCheckDetails details, int rollTotal) {
        if (details.difficulty() == null) throw new IllegalStateException("pending movement check has no typed difficulty");
        return resolution.resolvePlayerCheck(new ResolutionPort.PlayerCheckRequest(
                details.ruleReference(), details.diceExpression(), details.modifier(), details.difficulty(), rollTotal));
    }

    private CombatMapCheckDetails requireOwnedPendingCheck(java.util.UUID mapId, java.util.UUID operationId,
            CombatMapCheckSubmission submission) {
        if (!operationId.equals(submission.operationId())) throw new IllegalArgumentException("check operation does not match movement operation");
        CombatMapMoveResult pending = combatMap.movementOperation(mapId, operationId);
        CombatMapCheckDetails details = pending.pendingCheckDetails();
        if (details == null || !details.checkId().equals(submission.checkId())
                || !details.operationId().equals(submission.operationId())
                || !details.ownerPlayerId().equals(submission.ownerPlayerId())
                || details.actor() != submission.actor()) {
            throw new IllegalArgumentException("player roll does not belong to the pending movement check");
        }
        return details;
    }

    private CombatMapCheckDetails requireOwnedPendingCheck(java.util.UUID mapId, java.util.UUID operationId, SpatialCheckRollCommand command) {
        CombatMapMoveResult pending = combatMap.movementOperation(mapId, operationId);
        CombatMapCheckDetails details = pending.pendingCheckDetails();
        if (details == null || !details.checkId().equals(command.checkId())
                || !details.operationId().equals(command.operationId())
                || !details.ownerPlayerId().equals(command.ownerPlayerId())
                || details.actor() != command.actor()) {
            throw new IllegalArgumentException("player roll does not belong to the pending movement check");
        }
        return details;
    }
    private CombatMapCheckDetails requireEnemyPendingCheck(java.util.UUID mapId, java.util.UUID operationId,
            EnemyObservationRollCommand command) {
        CombatMapMoveResult pending = combatMap.movementOperation(mapId, operationId);
        CombatMapCheckDetails details = pending.pendingCheckDetails();
        if (details == null || !details.checkId().equals(command.checkId())
                || !details.operationId().equals(command.operationId())
                || !details.ownerPlayerId().equals(command.ownerPlayerId())
                || details.actor() != CombatMapCheckActor.ENEMY) {
            throw new IllegalArgumentException("enemy observation roll does not belong to the pending movement check");
        }
        return details;
    }
    public CombatMapMoveResult cancel(java.util.UUID mapId, java.util.UUID operationId, java.util.UUID cancelCommandId) {
        return combatMap.cancelMovementOperation(mapId, operationId, cancelCommandId);
    }
    public CombatMapSpatialResult observe(CombatMapSpatialActionCommand command) {
        authorizeSpatial(command, "OBSERVE");
        return combatMap.observe(command);
    }
    public CombatMapSpatialResult interact(CombatMapSpatialActionCommand command) {
        authorizeSpatial(command, "INTERACT");
        return combatMap.interact(command);
    }
    public CombatMapSpatialResult combatTurnStart(CombatMapSpatialTurnCommand command) { return combatMap.combatTurnStart(command); }
    public CombatMapSpatialResult advanceDurations(CombatMapSpatialTurnCommand command) { return combatMap.advanceDurations(command); }

    private void authorizeSpatial(CombatMapSpatialActionCommand command, String action) {
        Objects.requireNonNull(command, "spatial action command must not be null");
        spatialAuthorization.authorize(new SpatialActionAuthorizationPort.SpatialActionAuthorization(
                command.adventureId(), command.ownerPlayerId(), action, TurnResourceCost.actionOnly(), command.commandId(),
                command.adventureId() + "|" + command.mapId() + "|" + command.ownerPlayerId() + "|"
                        + command.tokenId() + "|" + command.cell().x() + "," + command.cell().y() + "|"
                        + command.expectedVersion() + "|" + action));
    }
}
