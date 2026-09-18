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
import java.util.UUID;
public final class CombatMapMovementService {
    private static final int MAXIMUM_RETRY_ATTEMPTS = 3;
    private final CombatMapRepository repository; private final AppliedEditionMovementPort movementPort; private final MovementResolutionOperationRepository operations;
    public CombatMapMovementService(CombatMapRepository repository, AppliedEditionMovementPort movementPort){this(repository, movementPort, new UnsupportedOperationRepository());}
    public CombatMapMovementService(CombatMapRepository repository, AppliedEditionMovementPort movementPort, MovementResolutionOperationRepository operations){this.repository=Objects.requireNonNull(repository);this.movementPort=Objects.requireNonNull(movementPort);this.operations=Objects.requireNonNull(operations);}
    public CombatMap movePlayerToken(MovePlayerTokenCommand command){
        Objects.requireNonNull(command);
        CombatMap replay = repository.findByCommandId(command.commandId()).orElse(null);
        if (replay != null) {
            if (!command.fingerprint().equals(replay.operationFingerprint())) throw new IllegalStateException("combat map command id reused with different payload");
            return replay;
        }
        CombatMap map=repository.findById(command.mapId()).orElseThrow(()->new CombatMapMovementDeniedException("map not found"));
        if(map.version()!=command.expectedVersion()) throw new CombatMapMovementStaleException();
        if (command.previewFingerprint() != null) {
            MovementPreview preview = preview(new MovementPreviewRequest(command.mapId(), command.playerId(), command.tokenId(),
                    command.path().orderedPositions().getLast(), command.waypoints(), command.appliedEdition(), command.expectedVersion()));
            if (!command.previewFingerprint().equals(preview.fingerprint())
                    || !command.path().orderedPositions().equals(preview.orderedPositions())
                    || command.path().distance() != preview.distance()) {
                throw new CombatMapMovementPreviewMismatchException();
            }
        }
        int maximum=movementPort.maximumMovement(map.ruleSetId(),command.appliedEdition());
        map.movePlayerToken(command.playerId(),command.tokenId(),command.path(),maximum);
        map.refreshVisibility(map.visibilitySnapshot() == null ? 0 : map.visibilitySnapshot().ruleTurn());
        repository.save(map, command.expectedVersion()+1, command.commandId(), command.fingerprint());
        map.markPersisted(command.expectedVersion()+1, command.commandId(), command.fingerprint());
        return map;
    }

    /** Starts or replays a durable movement reservation. Public map state changes only at the final save. */
    public MovementOperationResponse start(MovementStartRequest request) {
        Objects.requireNonNull(request);
        MovementResolutionOperation existing = operations.findOperationByCommandId(request.commandId()).orElse(null);
        if (existing != null) {
            if (!existing.fingerprint().equals(request.fingerprint())) throw new IllegalStateException("combat map command id reused with different payload");
            return response(existing);
        }
        if (operations.findActiveByMapId(request.mapId()).isPresent()) throw new MovementReservationConflictException();
        CombatMap map = repository.findById(request.mapId()).orElseThrow(() -> new CombatMapMovementDeniedException("map not found"));
        if (map.version() != request.expectedVersion()) throw new MovementVersionConflictException();
        int maximum = movementPort.maximumMovement(map.ruleSetId(), request.appliedEdition());
        map.validatePlayerMovement(request.playerId(), request.tokenId(), request.path(), maximum);
        MovementResolutionOperation operation = MovementResolutionOperation.start(UUID.randomUUID(), request.mapId(), request.commandId(), request.playerId(), request.tokenId(), request.path(), request.fingerprint(), request.expectedVersion());
        operations.reserve(operation);
        return resolve(map, operation);
    }

    public MovementOperationResponse resume(MapId mapId, UUID operationId) {
        MovementResolutionOperation operation = operations.findById(operationId).orElseThrow(() -> new IllegalArgumentException("movement reservation not found"));
        requireMap(operation, mapId);
        if (operation.status() == MovementOperationStatus.COMMITTED || operation.status() == MovementOperationStatus.CANCELLED) return response(operation);
        if (operation.status() == MovementOperationStatus.RETRY_WAIT) {
            operation.resumePreparing();
            operations.save(operation);
        }
        CombatMap map = repository.findById(operation.mapId()).orElseThrow(() -> new CombatMapMovementDeniedException("map not found"));
        rebuildStagedMap(map, operation);
        return resolve(map, operation);
    }

    public MovementOperationResponse query(MapId mapId, UUID operationId) { MovementResolutionOperation operation = operations.findById(operationId).orElseThrow(() -> new IllegalArgumentException("movement reservation not found")); requireMap(operation, mapId); return response(operation); }
    public MovementOperationResponse cancel(MapId mapId, UUID operationId) { MovementResolutionOperation operation = operations.findById(operationId).orElseThrow(() -> new IllegalArgumentException("movement reservation not found")); requireMap(operation, mapId); if (operation.status().active()) { operation.cancel(cancelledResult(operation, "CANCELLED")); operations.save(operation); } return response(operation); }

    private MovementOperationResponse resolve(CombatMap map, MovementResolutionOperation operation) {
        try {
            while (operation.cursor() < operation.requestedPath().orderedPositions().size() - 1) {
                int next = operation.cursor() + 1;
                map.advancePlayerToken(operation.playerId(), operation.tokenId(), operation.requestedPath().orderedPositions().get(next));
                map.refreshVisibility(map.visibilitySnapshot() == null ? 0 : map.visibilitySnapshot().ruleTurn());
                operation.advanceTo(next, map.playerTokenPosition(operation.playerId(), operation.tokenId()));
                operations.save(operation);
            }
            operation.readyToCommit(); operations.save(operation);
            long committedVersion = operation.expectedVersion() + 1;
            MovementResolutionResult result = new MovementResolutionResult(operation.requestedPath(), operation.traversedPath(), operation.currentCell(), committedVersion, List.of(), null);
            repository.commitMovementResolution(map, committedVersion, operation, result);
            operation.committed(result); operations.save(operation);
            return response(operation);
        } catch (RuntimeException exception) {
            if (operation.status().active()) {
                if (!retryable(exception) || !operation.retryWait(MAXIMUM_RETRY_ATTEMPTS)) {
                    operation.cancel(cancelledResult(operation, retryable(exception) ? "RETRY_EXHAUSTED" : "RESOLUTION_FAILED"));
                }
                operations.save(operation);
            }
            if (retryable(exception)) return response(operation);
            throw exception;
        }
    }
    private static void rebuildStagedMap(CombatMap map, MovementResolutionOperation operation) {
        map.validatePlayerMovement(operation.playerId(), operation.tokenId(), operation.requestedPath(), Integer.MAX_VALUE);
        for (int index = 1; index <= operation.cursor(); index++) {
            map.advancePlayerToken(operation.playerId(), operation.tokenId(), operation.requestedPath().orderedPositions().get(index));
            map.refreshVisibility(map.visibilitySnapshot() == null ? 0 : map.visibilitySnapshot().ruleTurn());
        }
        if (!map.playerTokenPosition(operation.playerId(), operation.tokenId()).equals(operation.currentCell()))
            throw new IllegalStateException("movement reservation cursor does not match its current cell");
    }
    private static void requireMap(MovementResolutionOperation operation, MapId mapId) {
        if (!operation.mapId().equals(mapId)) throw new IllegalArgumentException("movement reservation does not belong to this map");
    }
    private static boolean retryable(RuntimeException exception) {
        return exception instanceof com.dndmaster.combatmap.infrastructure.persistence.CombatMapPersistenceException;
    }
    private static MovementResolutionResult cancelledResult(MovementResolutionOperation operation, String reason) {
        return new MovementResolutionResult(operation.requestedPath(), operation.traversedPath(), operation.currentCell(),
                operation.expectedVersion(), List.of(), reason);
    }
    private static MovementOperationResponse response(MovementResolutionOperation operation) { return new MovementOperationResponse(operation.operationId(), operation.status(), operation.result()); }
    private static final class UnsupportedOperationRepository implements MovementResolutionOperationRepository {
        private IllegalStateException unsupported() { return new IllegalStateException("movement reservations require an operation repository"); }
        public java.util.Optional<MovementResolutionOperation> findById(UUID id) { throw unsupported(); } public java.util.Optional<MovementResolutionOperation> findOperationByCommandId(UUID id) { throw unsupported(); }
        public java.util.Optional<MovementResolutionOperation> findActiveByMapId(com.dndmaster.combatmap.domain.MapId id) { throw unsupported(); } public void reserve(MovementResolutionOperation operation) { throw unsupported(); } public void save(MovementResolutionOperation operation) { throw unsupported(); }
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
