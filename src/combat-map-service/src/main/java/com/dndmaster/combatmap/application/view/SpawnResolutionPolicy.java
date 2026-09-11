package com.dndmaster.combatmap.application.view;

import com.dndmaster.combatmap.domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;

public final class SpawnResolutionPolicy {
    private static final Logger LOGGER = LoggerFactory.getLogger(SpawnResolutionPolicy.class);
    public SpawnResolution resolve(GridSpec grid, Set<GridPosition> obstacles, Collection<Door> doors,
            Collection<GridPosition> occupied, MapActivationContext context, Optional<GridPosition> tacticalPlayerPlacement) {
        Set<GridPosition> playable = new HashSet<>();
        for (int y = 0; y < grid.height(); y++) for (int x = 0; x < grid.width(); x++) playable.add(new GridPosition(x, y));
        return resolve(grid, obstacles, doors, occupied, playable, context, tacticalPlayerPlacement);
    }
    public SpawnResolution resolve(GridSpec grid, Set<GridPosition> obstacles, Collection<Door> doors,
            Collection<GridPosition> occupied, MapActivationContext context,
            Optional<GridPosition> userConfirmedPlacement, Optional<GridPosition> tacticalPlayerPlacement) {
        Set<GridPosition> playable = new HashSet<>();
        for (int y = 0; y < grid.height(); y++) for (int x = 0; x < grid.width(); x++) playable.add(new GridPosition(x, y));
        return resolve(grid, obstacles, doors, occupied, playable, context, userConfirmedPlacement, tacticalPlayerPlacement);
    }
    public SpawnResolution resolve(GridSpec grid, Set<GridPosition> obstacles, Collection<Door> doors,
            Collection<GridPosition> occupied, Collection<GridPosition> playable, MapActivationContext context,
            Optional<GridPosition> tacticalPlayerPlacement) {
        return resolve(grid, obstacles, doors, occupied, playable, context, Optional.empty(), tacticalPlayerPlacement);
    }
    public SpawnResolution resolve(GridSpec grid, Set<GridPosition> obstacles, Collection<Door> doors,
            Collection<GridPosition> occupied, Collection<GridPosition> playable, MapActivationContext context,
            Optional<GridPosition> userConfirmedPlacement, Optional<GridPosition> tacticalPlayerPlacement) {
        return resolve(grid, obstacles, doors, occupied, playable, context, userConfirmedPlacement,
                tacticalPlayerPlacement, Optional.empty());
    }
    public SpawnResolution resolve(GridSpec grid, Set<GridPosition> obstacles, Collection<Door> doors,
            Collection<GridPosition> occupied, Collection<GridPosition> playable, MapActivationContext context,
            Optional<GridPosition> userConfirmedPlacement, Optional<GridPosition> tacticalPlayerPlacement,
            Optional<GridPosition> agentProposal) {
        Set<GridPosition> blocked = new HashSet<>(obstacles);
        doors.stream().filter(d -> !d.open()).map(Door::position).forEach(blocked::add);
        Set<GridPosition> used = new HashSet<>(occupied);
        Set<GridPosition> allowed = new HashSet<>(playable);
        Optional<SpawnResolution> tactical = evaluateCandidate(grid, blocked, used, allowed, tacticalPlayerPlacement,
                SpawnResolution.Source.EXPLICIT_TACTICAL);
        if (tactical.isPresent()) return tactical.get();
        Optional<SpawnResolution> user = evaluateCandidate(grid, blocked, used, allowed, userConfirmedPlacement,
                SpawnResolution.Source.USER_CONFIRMED);
        if (user.isPresent()) return user.get();
        Optional<SpawnResolution> activation = evaluateCandidate(grid, blocked, used, allowed, context.placementProposal(),
                SpawnResolution.Source.ACTIVATION_CANDIDATE);
        if (activation.isPresent()) return activation.get();
        Optional<SpawnResolution> agent = evaluateCandidate(grid, blocked, used, allowed, agentProposal,
                SpawnResolution.Source.AGENT_PROPOSAL);
        if (agent.isPresent()) return agent.get();
        throw new MapPlacementRequiredException();
    }

    private Optional<SpawnResolution> evaluateCandidate(GridSpec grid, Set<GridPosition> blocked, Set<GridPosition> used,
            Set<GridPosition> allowed, Optional<GridPosition> candidate, SpawnResolution.Source source) {
        if (candidate.isEmpty()) return Optional.empty();
        GridPosition position = candidate.get();
        List<String> reasons = new ArrayList<>();
        if (!grid.contains(position)) reasons.add("OUTSIDE_GRID");
        if (!allowed.contains(position)) reasons.add("NOT_PLAYABLE");
        if (blocked.contains(position)) reasons.add("BLOCKED_BY_OBSTACLE_OR_CLOSED_DOOR");
        if (used.contains(position)) reasons.add("OCCUPIED");
        if (reasons.isEmpty()) {
            LOGGER.info("map_spawn_candidate_valid source={} position={}", source, position);
            return Optional.of(new SpawnResolution(position, source));
        }
        LOGGER.info("map_spawn_candidate_rejected source={} position={} reasons={}", source, position, reasons);
        return Optional.empty();
    }
    public static boolean isValid(GridSpec grid, Collection<GridPosition> obstacles, Collection<Door> doors,
            Collection<GridPosition> occupied, Collection<GridPosition> playable, GridPosition position) {
        Set<GridPosition> blocked = new HashSet<>(obstacles);
        doors.stream().filter(door -> !door.open()).map(Door::position).forEach(blocked::add);
        return isValid(grid, blocked, new HashSet<>(occupied), new HashSet<>(playable), position);
    }
    private static boolean isValid(GridSpec grid, Set<GridPosition> blocked, Set<GridPosition> used, Set<GridPosition> allowed, GridPosition p) {
        return grid.contains(p) && allowed.contains(p) && !blocked.contains(p) && !used.contains(p);
    }
}
