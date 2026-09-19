package com.dndmaster.combatmap.application.spatial;

import com.dndmaster.combatmap.domain.CombatMap;
import com.dndmaster.combatmap.domain.CombatToken;
import com.dndmaster.combatmap.domain.GridPosition;
import com.dndmaster.combatmap.domain.LineOfSightQuery;
import com.dndmaster.combatmap.domain.TokenDiscovery;
import com.dndmaster.combatmap.domain.TokenId;
import com.dndmaster.combatmap.domain.TokenType;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/** 적에서 이동 중인 플레이어로 향하는 시선과 신규·재인지 경계만 소유한다. */
public final class HostileObservationResolver {
    private final LineOfSightQuery lineOfSight;

    public HostileObservationResolver() {
        this(new LineOfSightQuery());
    }

    public HostileObservationResolver(LineOfSightQuery lineOfSight) {
        this.lineOfSight = Objects.requireNonNull(lineOfSight, "line-of-sight query must not be null");
    }

    public Observation resolve(CombatMap map, GridPosition playerCell, Set<TokenId> previouslyObserved) {
        Objects.requireNonNull(map, "combat map must not be null");
        Objects.requireNonNull(playerCell, "player cell must not be null");
        Set<TokenId> prior = previouslyObserved == null ? Set.of() : Set.copyOf(previouslyObserved);
        Set<GridPosition> blockers = new HashSet<>(map.obstacles());
        map.doors().stream().filter(door -> !door.open()).map(com.dndmaster.combatmap.domain.Door::position)
                .forEach(blockers::add);
        boolean visible = false;
        boolean newlyObserved = false;
        for (CombatToken token : map.tokens()) {
            if (!isHostile(token) || !lineOfSight.clear(token.position(), playerCell, blockers, map.publicBoundaries())) continue;
            visible = true;
            newlyObserved |= !prior.contains(token.id());
        }
        return new Observation(visible, newlyObserved);
    }

    private static boolean isHostile(CombatToken token) {
        return (token.type() == TokenType.ENEMY || token.type() == TokenType.BOSS)
                && token.discovery() != TokenDiscovery.HIDDEN;
    }

    public record Observation(boolean lineOfSight, boolean newlyObserved) { }
}
