package com.dndmaster.combatmap.application.movement;
import com.dndmaster.combatmap.domain.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;
public final class CombatMapMovementService {
    private final CombatMapRepository repository; private final AppliedEditionMovementPort movementPort;
    public CombatMapMovementService(CombatMapRepository repository, AppliedEditionMovementPort movementPort){this.repository=Objects.requireNonNull(repository);this.movementPort=Objects.requireNonNull(movementPort);}
    public CombatMap movePlayerToken(MovePlayerTokenCommand command){
        Objects.requireNonNull(command);
        CombatMap replay = repository.findByCommandId(command.commandId()).orElse(null);
        if (replay != null) {
            if (!command.fingerprint().equals(replay.operationFingerprint())) throw new IllegalStateException("combat map command id reused with different payload");
            return replay;
        }
        CombatMap map=repository.findById(command.mapId()).orElseThrow(()->new CombatMapMovementDeniedException("map not found"));
        if(map.version()!=command.expectedVersion()) throw new CombatMapMovementStaleException();
        int maximum=movementPort.maximumMovement(map.ruleSetId(),command.appliedEdition());
        map.movePlayerToken(command.playerId(),command.tokenId(),command.path(),maximum);
        map.refreshVisibility(map.visibilitySnapshot() == null ? 0 : map.visibilitySnapshot().ruleTurn());
        repository.save(map, command.expectedVersion()+1, command.commandId(), command.fingerprint());
        map.markPersisted(command.expectedVersion()+1, command.commandId(), command.fingerprint());
        return map;
    }

    /**
     * Calculates a deterministic path from the player-safe map projection.
     * Spatial features and non-player tokens are intentionally never read here.
     */
    public MovementPreview preview(MovementPreviewRequest request) {
        Objects.requireNonNull(request, "movement preview request must not be null");
        CombatMap map = repository.findById(request.mapId())
                .orElseThrow(() -> new CombatMapMovementDeniedException("map not found"));
        if (map.version() != request.expectedVersion()) throw new CombatMapMovementStaleException();
        GridPosition start = map.playerTokenPosition(request.playerId(), request.tokenId());
        List<GridPosition> stops = new ArrayList<>(request.waypoints());
        stops.add(request.destination());
        List<GridPosition> completePath = new ArrayList<>();
        completePath.add(start);
        GridPosition current = start;
        for (GridPosition stop : stops) {
            if (!map.isPublicTraversable(stop)) throw new CombatMapMovementDeniedException("destination is not publicly traversable");
            List<GridPosition> segment = shortestPath(map, current, stop);
            if (segment.isEmpty()) throw new CombatMapMovementDeniedException("no public movement path exists");
            completePath.addAll(segment.subList(1, segment.size()));
            current = stop;
        }
        int distance = Math.multiplyExact(Math.max(0, completePath.size() - 1), map.grid().distanceUnit());
        int maximum = movementPort.maximumMovement(map.ruleSetId(), request.appliedEdition());
        if (distance > maximum) throw new CombatMapMovementDeniedException("path exceeds applied-edition movement allowance");
        return new MovementPreview(completePath, distance, map.version(), fingerprint(request, map, completePath, distance));
    }

    private static List<GridPosition> shortestPath(CombatMap map, GridPosition start, GridPosition destination) {
        if (start.equals(destination)) return List.of(start);
        Map<GridPosition, GridPosition> previous = new HashMap<>();
        Map<GridPosition, Integer> distance = new HashMap<>();
        PriorityQueue<Node> queue = new PriorityQueue<>(Comparator
                .comparingInt(Node::distance)
                .thenComparingInt(node -> node.position().y())
                .thenComparingInt(node -> node.position().x()));
        distance.put(start, 0);
        queue.add(new Node(start, 0));
        while (!queue.isEmpty()) {
            Node current = queue.remove();
            if (current.distance() != distance.getOrDefault(current.position(), Integer.MAX_VALUE)) continue;
            if (current.position().equals(destination)) break;
            for (GridPosition next : neighbors(current.position(), map.grid())) {
                if (!map.isPublicTraversable(next) || !map.publiclyTraversableBetween(current.position(), next)) continue;
                int nextDistance = current.distance() + 1;
                if (nextDistance >= distance.getOrDefault(next, Integer.MAX_VALUE)) continue;
                distance.put(next, nextDistance);
                previous.put(next, current.position());
                queue.add(new Node(next, nextDistance));
            }
        }
        if (!distance.containsKey(destination)) return List.of();
        List<GridPosition> result = new ArrayList<>();
        for (GridPosition current = destination; current != null; current = previous.get(current)) result.add(current);
        java.util.Collections.reverse(result);
        return result;
    }

    private static List<GridPosition> neighbors(GridPosition position, GridSpec grid) {
        List<GridPosition> result = new ArrayList<>(8);
        for (int y = Math.max(0, position.y() - 1); y <= Math.min(grid.height() - 1, position.y() + 1); y++) {
            for (int x = Math.max(0, position.x() - 1); x <= Math.min(grid.width() - 1, position.x() + 1); x++) {
                if (x != position.x() || y != position.y()) result.add(new GridPosition(x, y));
            }
        }
        return result;
    }

    private static String fingerprint(MovementPreviewRequest request, CombatMap map, List<GridPosition> path, int distance) {
        String publicState = map.grid().width() + "x" + map.grid().height() + "@" + map.grid().distanceUnit()
                + "|playable=" + allPositions(map.grid()).stream().filter(map::isPublicTraversable).toList()
                + "|boundaries=" + map.publicBoundaries().stream().map(MapBoundary::encoded).sorted().toList();
        String value = request.mapId() + "|" + request.playerId() + "|" + request.tokenId() + "|"
                + request.destination() + "|waypoints=" + request.waypoints() + "|edition=" + request.appliedEdition()
                + "|version=" + map.version() + "|distance=" + distance + "|path=" + path + "|" + publicState;
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("preview fingerprint unavailable", exception);
        }
    }

    private static List<GridPosition> allPositions(GridSpec grid) {
        List<GridPosition> positions = new ArrayList<>(grid.width() * grid.height());
        for (int y = 0; y < grid.height(); y++) for (int x = 0; x < grid.width(); x++) positions.add(new GridPosition(x, y));
        return positions;
    }

    private record Node(GridPosition position, int distance) {}
}
