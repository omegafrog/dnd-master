package com.dndmaster.combatmap.application.movement;

import com.dndmaster.combatmap.domain.CombatMap;
import com.dndmaster.combatmap.domain.CombatToken;
import com.dndmaster.combatmap.domain.GridPosition;
import com.dndmaster.combatmap.domain.HostileObservationRule;
import com.dndmaster.combatmap.domain.HostileObservationStatus;
import com.dndmaster.combatmap.domain.LineOfSightQuery;
import com.dndmaster.combatmap.domain.PlayerId;
import com.dndmaster.combatmap.domain.SpatialFeatureType;
import com.dndmaster.combatmap.domain.SpatialTrigger;
import com.dndmaster.combatmap.domain.TokenId;
import com.dndmaster.combatmap.domain.TokenType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** 적에서 이동 중인 플레이어로 향하는 시선과 인지 상태만 담당한다. */
public final class HostileObservationResolver {
    private final LineOfSightQuery lineOfSight;

    public HostileObservationResolver() {
        this(new LineOfSightQuery());
    }

    HostileObservationResolver(LineOfSightQuery lineOfSight) {
        this.lineOfSight = Objects.requireNonNull(lineOfSight, "line-of-sight query must not be null");
    }

    public HostileObservationResult evaluate(CombatMap map, PlayerId ownerPlayerId, TokenId playerTokenId,
            GridPosition playerCell, UUID operationId, int cursor) {
        Objects.requireNonNull(map, "combat map must not be null");
        Objects.requireNonNull(ownerPlayerId, "owner player id must not be null");
        Objects.requireNonNull(playerTokenId, "player token id must not be null");
        Objects.requireNonNull(playerCell, "player cell must not be null");
        Objects.requireNonNull(operationId, "operation id must not be null");
        List<CombatToken> hostiles = map.tokens().stream()
                .filter(token -> token.type() == TokenType.ENEMY || token.type() == TokenType.BOSS)
                .sorted(Comparator.comparing(token -> token.id().value()))
                .toList();
        for (CombatToken hostile : hostiles) {
            if (!lineOfSight.clear(hostile.position(), playerCell, new HashSet<>(map.obstacles()), map.boundaries())) {
                map.markHostileLost(hostile.id(), playerTokenId);
                continue;
            }
            HostileObservationStatus prior = map.hostileObservationStatus(hostile.id(), playerTokenId);
            if (prior == HostileObservationStatus.AWARE) {
                return new HostileObservationResult(HostileObservationResult.Status.CONTINUOUS,
                        hostile.id(), Optional.empty(), Optional.empty());
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
        return HostileObservationResult.none();
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
        for (CombatToken hostile : candidates(map)) {
            boolean visible = lineOfSight.clear(hostile.position(), playerCell,
                    new HashSet<>(map.obstacles()), map.boundaries());
            if (visible) map.markHostileAware(hostile.id(), playerTokenId);
            else map.markHostileLost(hostile.id(), playerTokenId);
        }
    }

    private static HostileObservationResult aware(CombatMap map, CombatToken hostile,
            TokenId playerTokenId, HostileObservationStatus prior) {
        map.markHostileAware(hostile.id(), playerTokenId);
        HostileObservationResult.Status status = prior == HostileObservationStatus.LOST
                ? HostileObservationResult.Status.REACQUIRED : HostileObservationResult.Status.NEW;
        MovementInterruption interruption = new MovementInterruption("HOSTILE_OBSERVED", List.of("HOSTILE_OBSERVED"));
        return new HostileObservationResult(status, hostile.id(), Optional.empty(), Optional.of(interruption));
    }

    private static List<CombatToken> candidates(CombatMap map) {
        return map.tokens().stream()
                .filter(token -> token.type() == TokenType.ENEMY || token.type() == TokenType.BOSS)
                .sorted(Comparator.comparing(token -> token.id().value()))
                .toList();
    }
}
