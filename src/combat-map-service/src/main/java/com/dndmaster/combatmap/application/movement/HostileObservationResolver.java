package com.dndmaster.combatmap.application.movement;

import com.dndmaster.combatmap.domain.CombatMap;
import com.dndmaster.combatmap.domain.CombatToken;
import com.dndmaster.combatmap.domain.GridPosition;
import com.dndmaster.combatmap.domain.HostileObservationRule;
import com.dndmaster.combatmap.domain.HostileObservationStatus;
import com.dndmaster.combatmap.domain.PlayerId;
import com.dndmaster.combatmap.domain.SpatialTrigger;
import com.dndmaster.combatmap.domain.Door;
import com.dndmaster.combatmap.domain.LineOfSightQuery;
import com.dndmaster.combatmap.domain.VisibilityProfile;
import com.dndmaster.combatmap.domain.TokenId;
import com.dndmaster.combatmap.domain.TokenType;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** 적에서 이동 중인 플레이어로 향하는 시선과 인지 상태만 담당한다. */
public final class HostileObservationResolver {
    private final com.dndmaster.combatmap.application.spatial.SpatialFeatureDetectionPolicy detectionPolicy;
    private final LineOfSightQuery lineOfSight;
    private final VisibilityProfile visibilityProfile;

    public HostileObservationResolver() {
        this(new com.dndmaster.combatmap.application.spatial.SpatialFeatureDetectionPolicy());
    }

    public HostileObservationResolver(com.dndmaster.combatmap.application.spatial.SpatialFeatureDetectionPolicy detectionPolicy) {
        this.detectionPolicy = Objects.requireNonNull(detectionPolicy, "detection policy must not be null");
        this.lineOfSight = new LineOfSightQuery();
        this.visibilityProfile = new VisibilityProfile(detectionPolicy.maxRangeCells());
    }

    public HostileObservationResult evaluate(CombatMap map, PlayerId ownerPlayerId, TokenId playerTokenId,
            GridPosition playerCell, UUID operationId, int cursor) {
        Objects.requireNonNull(map, "combat map must not be null");
        Objects.requireNonNull(ownerPlayerId, "owner player id must not be null");
        Objects.requireNonNull(playerTokenId, "player token id must not be null");
        Objects.requireNonNull(playerCell, "player cell must not be null");
        Objects.requireNonNull(operationId, "operation id must not be null");
        for (var observation : map.hostileObservations()) {
            if (observation.playerTokenId().equals(playerTokenId)
                    && observation.status() == HostileObservationStatus.AWARE
                    && map.token(observation.hostileTokenId())
                    .map(token -> !hostileLineOfSight(map, playerCell, token.position())).orElse(true)) {
                map.markHostileLost(observation.hostileTokenId(), playerTokenId);
            }
        }
        List<CombatToken> hostiles = visibleHostiles(map, playerCell).stream()
                .filter(token -> token.type() == TokenType.ENEMY || token.type() == TokenType.BOSS)
                .sorted(Comparator.comparing(token -> token.id().value()))
                .toList();
        HostileObservationResult continuous = null;
        for (CombatToken hostile : hostiles) {
            HostileObservationStatus prior = map.hostileObservationStatus(hostile.id(), playerTokenId);
            if (prior == HostileObservationStatus.AWARE) {
                if (continuous == null) {
                    continuous = new HostileObservationResult(HostileObservationResult.Status.CONTINUOUS,
                            hostile.id(), Optional.empty(), Optional.empty());
                }
                continue;
            }
            Optional<HostileObservationRule> rule = hostile.hostileObservationRule();
            if (rule.isPresent()) {
                HostileObservationRule value = rule.orElseThrow();
                MovementCheckRequest request = MovementCheckRequest.hostile(
                        UUID.randomUUID(), operationId, hostile.id().value(), SpatialTrigger.BECOME_VISIBLE,
                        value.ruleReference(), value.diceExpression(), value.modifier(), value.difficulty(), value.mode(),
                        MovementCheckOwner.enemy(ownerPlayerId), playerCell, cursor);
                return new HostileObservationResult(HostileObservationResult.Status.CHECK_REQUIRED,
                        hostile.id(), Optional.of(request), Optional.empty());
            }
            return aware(map, hostile, playerTokenId, prior);
        }
        return continuous == null ? HostileObservationResult.none() : continuous;
    }

    public HostileObservationResult resolveCheck(CombatMap map, TokenId hostileTokenId, TokenId playerTokenId,
            boolean success, GridPosition playerCell) {
        if (!success) {
            map.markHostileLost(hostileTokenId, playerTokenId);
            return HostileObservationResult.none();
        }
        CombatToken hostile = map.tokens().stream().filter(token -> token.id().equals(hostileTokenId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("hostile token not found"));
        HostileObservationStatus prior = map.hostileObservationStatus(hostileTokenId, playerTokenId);
        return aware(map, hostile, playerTokenId, prior);
    }

    /** Replays awareness state for cells already traversed by a recovered operation. */
    public void rebuildAwareness(CombatMap map, TokenId playerTokenId, GridPosition playerCell) {
        for (CombatToken hostile : visibleHostiles(map, playerCell)) {
            map.markHostileAware(hostile.id(), playerTokenId);
        }
    }

    private List<CombatToken> visibleHostiles(CombatMap map, GridPosition playerCell) {
        return map.tokens().stream()
                .filter(token -> (token.type() == TokenType.ENEMY || token.type() == TokenType.BOSS)
                        && hostileLineOfSight(map, playerCell, token.position()))
                .sorted(Comparator.comparing(token -> token.id().value()))
                .toList();
    }

    /** Enemy awareness has its own geometry policy; it must not depend on the player's projection. */
    private boolean hostileLineOfSight(CombatMap map, GridPosition origin, GridPosition target) {
        if (Math.max(Math.abs(target.x() - origin.x()), Math.abs(target.y() - origin.y()))
                > visibilityProfile.maxRangeCells()) return false;
        Set<GridPosition> blockers = new HashSet<>(map.obstacles());
        map.doors().stream().filter(door -> !door.open()).map(Door::position).forEach(blockers::add);
        return lineOfSight.clear(origin, target, blockers, map.boundaries());
    }

    private static HostileObservationResult aware(CombatMap map, CombatToken hostile,
            TokenId playerTokenId, HostileObservationStatus prior) {
        map.markHostileAware(hostile.id(), playerTokenId);
        HostileObservationResult.Status status = prior == HostileObservationStatus.LOST
                ? HostileObservationResult.Status.REACQUIRED : HostileObservationResult.Status.NEW;
        MovementInterruption interruption = new MovementInterruption("HOSTILE_OBSERVED", List.of("HOSTILE_OBSERVED"));
        return new HostileObservationResult(status, hostile.id(), Optional.empty(), Optional.of(interruption));
    }

}
