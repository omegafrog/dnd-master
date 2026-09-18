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
    private MovementCheckRequest pendingCheck; private List<MovementCheckOutcome> checkOutcomes = new ArrayList<>();
    private int retryCount; private long persistenceVersion; private MovementOperationStatus retryResumeStatus;

    private MovementResolutionOperation(UUID operationId, MapId mapId, UUID commandId, PlayerId playerId, TokenId tokenId,
            MovementPath requestedPath, String fingerprint, long expectedVersion) {
        this.operationId = Objects.requireNonNull(operationId); this.mapId = Objects.requireNonNull(mapId); this.commandId = Objects.requireNonNull(commandId);
        this.playerId = Objects.requireNonNull(playerId); this.tokenId = Objects.requireNonNull(tokenId); this.requestedPath = Objects.requireNonNull(requestedPath);
        this.fingerprint = Objects.requireNonNull(fingerprint); this.expectedVersion = expectedVersion; this.status = MovementOperationStatus.PREPARING;
        this.cursor = 0; this.currentCell = requestedPath.orderedPositions().getFirst(); this.traversedPath = new ArrayList<>(List.of(currentCell));
        this.retryResumeStatus = MovementOperationStatus.PREPARING;
    }
    public static MovementResolutionOperation start(UUID operationId, MapId mapId, UUID commandId, PlayerId playerId, TokenId tokenId,
            MovementPath requestedPath, String fingerprint, long expectedVersion) {
        return new MovementResolutionOperation(operationId, mapId, commandId, playerId, tokenId, requestedPath, fingerprint, expectedVersion);
    }
    /** Rehydrates only coordinator state; the Combat Map remains the public-state source of truth. */
    public static MovementResolutionOperation restore(UUID operationId, MapId mapId, UUID commandId, PlayerId playerId, TokenId tokenId,
            MovementPath requestedPath, String fingerprint, long expectedVersion, MovementOperationStatus status,
            int cursor, GridPosition currentCell, List<GridPosition> traversedPath, MovementResolutionResult result,
            int retryCount, long persistenceVersion, MovementOperationStatus retryResumeStatus) {
        return restore(operationId, mapId, commandId, playerId, tokenId, requestedPath, fingerprint, expectedVersion, status,
                cursor, currentCell, traversedPath, result, retryCount, persistenceVersion, retryResumeStatus, null, List.of());
    }
    public static MovementResolutionOperation restore(UUID operationId, MapId mapId, UUID commandId, PlayerId playerId, TokenId tokenId,
            MovementPath requestedPath, String fingerprint, long expectedVersion, MovementOperationStatus status,
            int cursor, GridPosition currentCell, List<GridPosition> traversedPath, MovementResolutionResult result,
            int retryCount, long persistenceVersion, MovementOperationStatus retryResumeStatus,
            MovementCheckRequest pendingCheck, List<MovementCheckOutcome> checkOutcomes) {
        MovementResolutionOperation operation = new MovementResolutionOperation(operationId, mapId, commandId, playerId, tokenId,
                requestedPath, fingerprint, expectedVersion);
        operation.status = Objects.requireNonNull(status);
        operation.cursor = cursor;
        operation.currentCell = Objects.requireNonNull(currentCell);
        operation.traversedPath = new ArrayList<>(traversedPath);
        operation.result = result;
        operation.retryCount = retryCount;
        operation.persistenceVersion = persistenceVersion;
        operation.retryResumeStatus = retryResumeStatus == null ? MovementOperationStatus.PREPARING : retryResumeStatus;
        operation.pendingCheck = pendingCheck;
        operation.checkOutcomes = new ArrayList<>(checkOutcomes == null ? List.of() : checkOutcomes);
        return operation;
    }
    public void advanceTo(int nextCursor, GridPosition cell) {
        if (status != MovementOperationStatus.PREPARING || nextCursor != cursor + 1) throw new IllegalStateException("movement operation cannot advance");
        cursor = nextCursor; currentCell = Objects.requireNonNull(cell); traversedPath = new ArrayList<>(requestedPath.orderedPositions().subList(0, cursor + 1));
    }
    public void requestCheck(MovementCheckRequest request) {
        if (status != MovementOperationStatus.PREPARING) throw new IllegalStateException("movement operation is not preparing");
        if (!request.operationId().equals(operationId)) throw new IllegalArgumentException("check request belongs to another operation");
        pendingCheck = Objects.requireNonNull(request);
        status = MovementOperationStatus.CHECK_PENDING;
    }
    public void resumeFromCheck(MovementCheckResult result) {
        if (status != MovementOperationStatus.CHECK_PENDING || pendingCheck == null) {
            throw new IllegalStateException("movement operation is not waiting for a check");
        }
        if (!operationId.equals(result.operationId()) || !pendingCheck.checkId().equals(result.checkId())
                || !pendingCheck.owner().equals(result.owner())) {
            throw new IllegalArgumentException("check result does not belong to this movement operation");
        }
        checkOutcomes.add(new MovementCheckOutcome(pendingCheck.featureId(), result.success()));
        pendingCheck = null;
        status = MovementOperationStatus.PREPARING;
    }
    public boolean retryWait(int maximumRetries) { if (!status.active()) throw new IllegalStateException("movement operation is terminal"); if (status != MovementOperationStatus.RETRY_WAIT) retryResumeStatus = status; if (++retryCount > maximumRetries) return false; status = MovementOperationStatus.RETRY_WAIT; return true; }
    public void resumeAfterRetry() { if (status != MovementOperationStatus.RETRY_WAIT) throw new IllegalStateException("movement operation is not waiting"); status = retryResumeStatus; }
    public void resumePreparing() { resumeAfterRetry(); }
    public void readyToCommit() { if (status != MovementOperationStatus.PREPARING) throw new IllegalStateException("movement operation is not preparing"); status = MovementOperationStatus.READY_TO_COMMIT; }
    public void readyToCommit(MovementResolutionResult value) { result = Objects.requireNonNull(value); readyToCommit(); }
    public void committed(MovementResolutionResult value) { if (status != MovementOperationStatus.READY_TO_COMMIT) throw new IllegalStateException("movement operation is not ready"); result = Objects.requireNonNull(value); status = MovementOperationStatus.COMMITTED; }
    public void cancel(MovementResolutionResult value) { if (!status.active()) throw new IllegalStateException("movement operation is terminal"); result = Objects.requireNonNull(value); status = MovementOperationStatus.CANCELLED; }
    public UUID operationId() { return operationId; } public MapId mapId() { return mapId; } public UUID commandId() { return commandId; }
    public PlayerId playerId() { return playerId; } public TokenId tokenId() { return tokenId; } public MovementPath requestedPath() { return requestedPath; }
    public String fingerprint() { return fingerprint; } public long expectedVersion() { return expectedVersion; } public MovementOperationStatus status() { return status; }
    public int cursor() { return cursor; } public GridPosition currentCell() { return currentCell; } public List<GridPosition> traversedPath() { return List.copyOf(traversedPath); }
    public MovementResolutionResult result() { return result; } public int retryCount() { return retryCount; }
    public MovementCheckRequest pendingCheck() { return pendingCheck; }
    public List<MovementCheckOutcome> checkOutcomes() { return List.copyOf(checkOutcomes); }
    public java.util.Optional<Boolean> checkOutcome(UUID featureId) { return checkOutcomes.stream().filter(value -> value.featureId().equals(featureId)).reduce((first, ignored) -> ignored).map(MovementCheckOutcome::success); }
    public MovementOperationStatus retryResumeStatus() { return retryResumeStatus; }
    public long persistenceVersion() { return persistenceVersion; } public void markPersisted(long value) { persistenceVersion = value; }
}
