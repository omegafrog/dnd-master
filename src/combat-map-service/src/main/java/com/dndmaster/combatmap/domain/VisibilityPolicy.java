package com.dndmaster.combatmap.domain;

import java.util.*;

/** Pure player-visibility rules. Hidden token data stays in the combat-map context. */
public final class VisibilityPolicy {
    public VisibilitySnapshot calculate(GridSpec grid, Set<GridPosition> origins,
            Set<GridPosition> exploredBefore, Set<GridPosition> blockers,
            List<CombatToken> tokens, Collection<LastSeenState> previousLastSeen, long ruleTurn) {
        Objects.requireNonNull(grid); Objects.requireNonNull(origins); Objects.requireNonNull(blockers);
        Set<GridPosition> current = new HashSet<>();
        for (int y = 0; y < grid.height(); y++) for (int x = 0; x < grid.width(); x++) {
            GridPosition cell = new GridPosition(x, y);
            if (origins.stream().anyMatch(origin -> lineClear(origin, cell, blockers))) current.add(cell);
        }
        Set<GridPosition> explored = new HashSet<>(exploredBefore);
        explored.addAll(current);
        Set<TokenId> observed = new HashSet<>();
        List<LastSeenState> lastSeen = new ArrayList<>();
        Map<TokenId, LastSeenState> previous = new HashMap<>();
        for (LastSeenState state : previousLastSeen) previous.put(state.tokenId(), state);
        for (CombatToken token : tokens) {
            if (current.contains(token.position())) {
                observed.add(token.id());
                if (token.type() != TokenType.PLAYER) {
                    lastSeen.add(new LastSeenState(token.id(), token.type(), token.position(), ruleTurn + 2));
                }
            } else {
                LastSeenState prior = previous.get(token.id());
                if (prior != null && prior.expiresAtTurn() > ruleTurn) {
                    lastSeen.add(new LastSeenState(token.id(), prior.type(), prior.position(), ruleTurn + 1));
                }
            }
        }
        return new VisibilitySnapshot(current, explored, observed, lastSeen, ruleTurn);
    }

    private boolean lineClear(GridPosition from, GridPosition to, Set<GridPosition> blockers) {
        int dx = Math.abs(to.x() - from.x()), dy = Math.abs(to.y() - from.y());
        int sx = Integer.compare(to.x(), from.x()), sy = Integer.compare(to.y(), from.y());
        int x = from.x(), y = from.y(), error = dx - dy;
        while (x != to.x() || y != to.y()) {
            int twice = error * 2;
            if (twice > -dy) { error -= dy; x += sx; }
            if (twice < dx) { error += dx; y += sy; }
            GridPosition step = new GridPosition(x, y);
            if (!step.equals(to) && blockers.contains(step)) return false;
        }
        return true;
    }
}
