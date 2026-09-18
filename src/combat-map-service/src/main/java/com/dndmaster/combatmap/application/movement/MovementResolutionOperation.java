package com.dndmaster.combatmap.application.movement;

import com.dndmaster.combatmap.domain.GridPosition;
import com.dndmaster.combatmap.domain.MapId;
import com.dndmaster.combatmap.domain.MovementPath;
import com.dndmaster.combatmap.domain.PlayerId;
import com.dndmaster.combatmap.domain.TokenId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Durable, non-public work record. It never changes a map until committed. */
public final class MovementResolutionOperation {
    private final UUID operationId; private final MapId mapId; private final UUID commandId; private final PlayerId playerId;
    private final TokenId tokenId; private final MovementPath requestedPath; private final String fingerprint; private final long expectedVersion;
    private MovementOperationStatus status; private int cursor; private GridPosition currentCell; private List<GridPosition> traversedPath; private MovementResolutionResult result;

    private MovementResolutionOperation(UUID operationId, MapId mapId, UUID commandId, PlayerId playerId, TokenId tokenId,
            MovementPath requestedPath, String fingerprint, long expectedVersion) {
        this.operationId = Objects.requireNonNull(operationId); this.mapId = Objects.requireNonNull(mapId); this.commandId = Objects.requireNonNull(commandId);
        this.playerId = Objects.requireNonNull(playerId); this.tokenId = Objects.requireNonNull(tokenId); this.requestedPath = Objects.requireNonNull(requestedPath);
        this.fingerprint = Objects.requireNonNull(fingerprint); this.expectedVersion = expectedVersion; this.status = MovementOperationStatus.PREPARING;
        this.cursor = 0; this.currentCell = requestedPath.orderedPositions().getFirst(); this.traversedPath = new ArrayList<>(List.of(currentCell));
    }
    public static MovementResolutionOperation start(UUID operationId, MapId mapId, UUID commandId, PlayerId playerId, TokenId tokenId,
            MovementPath requestedPath, String fingerprint, long expectedVersion) {
        return new MovementResolutionOperation(operationId, mapId, commandId, playerId, tokenId, requestedPath, fingerprint, expectedVersion);
    }
    /** Rehydrates only coordinator state; the Combat Map remains the public-state source of truth. */
    public static MovementResolutionOperation restore(UUID operationId, MapId mapId, UUID commandId, PlayerId playerId, TokenId tokenId,
            MovementPath requestedPath, String fingerprint, long expectedVersion, MovementOperationStatus status,
            int cursor, GridPosition currentCell, List<GridPosition> traversedPath, MovementResolutionResult result) {
        MovementResolutionOperation operation = new MovementResolutionOperation(operationId, mapId, commandId, playerId, tokenId,
                requestedPath, fingerprint, expectedVersion);
        operation.status = Objects.requireNonNull(status);
        operation.cursor = cursor;
        operation.currentCell = Objects.requireNonNull(currentCell);
        operation.traversedPath = new ArrayList<>(traversedPath);
        operation.result = result;
        return operation;
    }
    public void advanceTo(int nextCursor, GridPosition cell) {
        if (status != MovementOperationStatus.PREPARING || nextCursor != cursor + 1) throw new IllegalStateException("movement operation cannot advance");
        cursor = nextCursor; currentCell = Objects.requireNonNull(cell); traversedPath = new ArrayList<>(requestedPath.orderedPositions().subList(0, cursor + 1));
    }
    public void retryWait() { if (!status.active()) throw new IllegalStateException("movement operation is terminal"); status = MovementOperationStatus.RETRY_WAIT; }
    public void resumePreparing() { if (status != MovementOperationStatus.RETRY_WAIT) throw new IllegalStateException("movement operation is not waiting"); status = MovementOperationStatus.PREPARING; }
    public void readyToCommit() { if (status != MovementOperationStatus.PREPARING) throw new IllegalStateException("movement operation is not preparing"); status = MovementOperationStatus.READY_TO_COMMIT; }
    public void committed(MovementResolutionResult value) { if (status != MovementOperationStatus.READY_TO_COMMIT) throw new IllegalStateException("movement operation is not ready"); result = Objects.requireNonNull(value); status = MovementOperationStatus.COMMITTED; }
    public void cancel() { if (!status.active()) throw new IllegalStateException("movement operation is terminal"); status = MovementOperationStatus.CANCELLED; }
    public UUID operationId() { return operationId; } public MapId mapId() { return mapId; } public UUID commandId() { return commandId; }
    public PlayerId playerId() { return playerId; } public TokenId tokenId() { return tokenId; } public MovementPath requestedPath() { return requestedPath; }
    public String fingerprint() { return fingerprint; } public long expectedVersion() { return expectedVersion; } public MovementOperationStatus status() { return status; }
    public int cursor() { return cursor; } public GridPosition currentCell() { return currentCell; } public List<GridPosition> traversedPath() { return List.copyOf(traversedPath); }
    public MovementResolutionResult result() { return result; }
}
