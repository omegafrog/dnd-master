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
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(CombatMapMovementService.class);
    private final CombatMapRepository repository; private final AppliedEditionMovementPort movementPort; private final MovementResolutionOperationRepository operations;
    private final MovementInterruptionPolicy interruptionPolicy;
    private final com.dndmaster.combatmap.application.spatial.SpatialFeatureDetectionPolicy detectionPolicy;
    private final com.dndmaster.combatmap.application.spatial.SpatialTriggerResolver triggerResolver;
    private final MovementCheckResolver checkResolver;
    public CombatMapMovementService(CombatMapRepository repository, AppliedEditionMovementPort movementPort){this(repository, movementPort, new UnsupportedOperationRepository(), MovementInterruptionPolicy.publicSpatialFeatures(), MovementCheckResolver.pending());}
    public CombatMapMovementService(CombatMapRepository repository, AppliedEditionMovementPort movementPort, MovementResolutionOperationRepository operations){this(repository, movementPort, operations, MovementInterruptionPolicy.publicSpatialFeatures(), MovementCheckResolver.pending());}
    public CombatMapMovementService(CombatMapRepository repository, AppliedEditionMovementPort movementPort,
            MovementResolutionOperationRepository operations, MovementInterruptionPolicy interruptionPolicy){this(repository, movementPort, operations, interruptionPolicy, MovementCheckResolver.pending());}
    public CombatMapMovementService(CombatMapRepository repository, AppliedEditionMovementPort movementPort,
            MovementResolutionOperationRepository operations, MovementInterruptionPolicy interruptionPolicy,
            MovementCheckResolver checkResolver){this.repository=Objects.requireNonNull(repository);this.movementPort=Objects.requireNonNull(movementPort);this.operations=Objects.requireNonNull(operations);this.interruptionPolicy=Objects.requireNonNull(interruptionPolicy);this.detectionPolicy=new com.dndmaster.combatmap.application.spatial.SpatialFeatureDetectionPolicy();this.triggerResolver=new com.dndmaster.combatmap.application.spatial.SpatialTriggerResolver();this.checkResolver=Objects.requireNonNull(checkResolver);}
    public CombatMap movePlayerToken(MovePlayerTokenCommand command){
        Objects.requireNonNull(command);
        CombatMap replay = repository.findByCommandId(command.commandId()).orElse(null);
        if (replay != null) {
            if (!command.fingerprint().equals(replay.operationFingerprint())) throw new MovementCommandConflictException();
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
        if (request.previewFingerprint() == null || request.previewFingerprint().isBlank()) {
            throw new MovementPreviewRequiredException();
        }
        MovementResolutionOperation existing = operations.findOperationByCommandId(request.commandId()).orElse(null);
        if (existing != null) {
            if (!existing.fingerprint().equals(request.fingerprint())) throw new MovementCommandConflictException();
            return response(existing);
        }
        if (operations.findActiveByMapId(request.mapId()).isPresent()) throw new MovementReservationConflictException();
        CombatMap map = repository.findById(request.mapId()).orElseThrow(() -> new CombatMapMovementDeniedException("map not found"));
        if (map.version() != request.expectedVersion()) throw new MovementVersionConflictException();
        int maximum = movementPort.maximumMovement(map.ruleSetId(), request.appliedEdition());
        map.validatePlayerMovement(request.playerId(), request.tokenId(), request.path(), maximum);
        validateStagedPreview(request);
        MovementResolutionOperation operation = MovementResolutionOperation.start(UUID.randomUUID(), request.mapId(), request.commandId(), request.playerId(), request.tokenId(), request.path(), request.fingerprint(), request.expectedVersion());
        MovementResolutionOperation reserved = operations.reserve(operation);
        if (reserved != operation) return response(reserved);
        return resolve(map, operation);
    }

    public MovementOperationResponse resume(MapId mapId, UUID operationId) {
        MovementResolutionOperation operation = operations.findById(operationId).orElseThrow(() -> new IllegalArgumentException("movement reservation not found"));
        requireMap(operation, mapId);
        if (operation.status() == MovementOperationStatus.COMMITTED || operation.status() == MovementOperationStatus.CANCELLED) return response(operation);
        if (operation.status() == MovementOperationStatus.CHECK_PENDING) return response(operation);
        if (operation.status() == MovementOperationStatus.RETRY_WAIT) {
            operation.resumeAfterRetry();
            operations.save(operation);
        }
        CombatMap map = repository.findById(operation.mapId()).orElseThrow(() -> new CombatMapMovementDeniedException("map not found"));
        rebuildStagedMap(map, operation);
        return resolve(map, operation);
    }

    public MovementOperationResponse resume(MapId mapId, UUID operationId, MovementCheckResult checkResult) {
        MovementResolutionOperation operation = operations.findById(operationId).orElseThrow(() -> new IllegalArgumentException("movement reservation not found"));
        requireMap(operation, mapId);
        boolean replay = operation.resumeFromCheck(Objects.requireNonNull(checkResult));
        if (replay) return response(operation);
        operations.save(operation);
        CombatMap map = repository.findById(operation.mapId()).orElseThrow(() -> new CombatMapMovementDeniedException("map not found"));
        rebuildStagedMap(map, operation);
        return resolve(map, operation);
    }

    /** Starts a durable observation action at the player's current cell. */
    public MovementOperationResponse observe(MapId mapId, PlayerId playerId, TokenId tokenId,
            GridPosition cell, long expectedVersion, UUID commandId) {
        Objects.requireNonNull(cell, "observation cell must not be null");
        Objects.requireNonNull(commandId, "observation command id must not be null");
        String fingerprint = mapId + "|" + playerId + "|" + tokenId + "|OBSERVE|" + cell;
        MovementResolutionOperation existing = operations.findOperationByCommandId(commandId).orElse(null);
        if (existing != null) {
            if (!existing.fingerprint().equals(fingerprint)) throw new MovementCommandConflictException();
            return response(existing);
        }
        if (operations.findActiveByMapId(mapId).isPresent()) throw new MovementReservationConflictException();
        CombatMap map = repository.findById(mapId).orElseThrow(() -> new CombatMapMovementDeniedException("map not found"));
        if (map.version() != expectedVersion) throw new MovementVersionConflictException();
        if (!map.playerTokenPosition(playerId, tokenId).equals(cell)
                || map.visibilitySnapshot() == null || !map.visibilitySnapshot().current().contains(cell)) {
            throw new IllegalArgumentException("observation must target the player's current visible cell");
        }
        MovementResolutionOperation operation = MovementResolutionOperation.start(UUID.randomUUID(), mapId, commandId,
                playerId, tokenId, new MovementPath(List.of(cell), 0), fingerprint, expectedVersion);
        MovementResolutionOperation reserved = operations.reserve(operation);
        if (reserved != operation) return response(reserved);
        return resolve(map, operation);
    }

    /** Startup recovery entry point. Each operation resumes from its persisted cursor and state. */
    public List<MovementOperationResponse> recoverIncompleteOperations() {
        return recoverSafely(false);
    }

    /** Runtime polling retries only work that has durably entered retry wait. */
    public List<MovementOperationResponse> retryWaitingOperations() {
        return recoverSafely(true);
    }

    /** Runtime polling resumes active work only after it has stopped making durable progress. */
    public List<MovementOperationResponse> recoverStalledOperations(java.time.Instant cutoff) {
        try {
            return recover(operations.findStalledBefore(Objects.requireNonNull(cutoff)));
        } catch (RuntimeException failure) {
            LOGGER.warn("movement_operation_stalled_recovery_load_deferred failure={}",
                    failure.getClass().getSimpleName());
            return List.of();
        }
    }

    private List<MovementOperationResponse> recoverSafely(boolean retryWaitOnly) {
        try {
            List<MovementResolutionOperation> recoverable = operations.findRecoverable();
            if (retryWaitOnly) {
                recoverable = recoverable.stream()
                        .filter(operation -> operation.status() == MovementOperationStatus.RETRY_WAIT)
                        .toList();
            }
            return recover(recoverable);
        } catch (RuntimeException failure) {
            LOGGER.warn("movement_operation_recovery_load_deferred failure={}",
                    failure.getClass().getSimpleName());
            return List.of();
        }
    }

    private List<MovementOperationResponse> recover(List<MovementResolutionOperation> operationsToRecover) {
        List<MovementOperationResponse> recovered = new ArrayList<>();
        for (MovementResolutionOperation operation : operationsToRecover) {
            try {
                recovered.add(resume(operation.mapId(), operation.operationId()));
            } catch (RuntimeException failure) {
                LOGGER.warn("movement_operation_recovery_deferred operationId={} mapId={} failure={}",
                        operation.operationId(), operation.mapId(), failure.getClass().getSimpleName());
                operations.findById(operation.operationId()).map(CombatMapMovementService::response).ifPresent(recovered::add);
            }
        }
        return List.copyOf(recovered);
    }

    public MovementOperationResponse query(MapId mapId, UUID operationId) { MovementResolutionOperation operation = operations.findById(operationId).orElseThrow(() -> new IllegalArgumentException("movement reservation not found")); requireMap(operation, mapId); return response(operation); }
    public java.util.Optional<MovementOperationResponse> latest(MapId mapId) {
        return operations.findLatestByMapId(mapId).map(CombatMapMovementService::response);
    }
    public MovementOperationResponse cancel(MapId mapId, UUID operationId, UUID cancelCommandId) {
        MovementResolutionOperation operation = operations.findById(operationId)
                .orElseThrow(() -> new IllegalArgumentException("movement reservation not found"));
        requireMap(operation, mapId);
        Objects.requireNonNull(cancelCommandId, "cancel command id must not be null");
        operations.findOperationByCancelCommandId(cancelCommandId).ifPresent(existing -> {
            if (!existing.operationId().equals(operationId)) throw new MovementCommandConflictException();
        });
        if (operation.cancelCommandId() != null && !operation.cancelCommandId().equals(cancelCommandId)) {
            throw new MovementCommandConflictException();
        }
        boolean firstCancellationCommand = operation.cancelCommandId() == null;
        if (firstCancellationCommand) operation.recordCancelCommand(cancelCommandId);
        if (operation.status().active()) {
            operation.cancel(cancelledResult(operation, "CANCELLED"));
            operations.save(operation);
        } else if (firstCancellationCommand) {
            operations.save(operation);
        }
        return response(operation);
    }

    private MovementOperationResponse resolve(CombatMap map, MovementResolutionOperation operation) {
        try {
            if (operation.status() == MovementOperationStatus.READY_TO_COMMIT) return commitPrepared(map, operation);
            if (operation.requestedPath().orderedPositions().size() == 1) return resolveObservation(map, operation);
            List<String> publicEvents = new ArrayList<>();
            while (operation.cursor() < operation.requestedPath().orderedPositions().size() - 1) {
                int next = operation.cursor() + 1;
                for (MovementCheckOutcome outcome : operation.checkOutcomes()) {
                    if (!outcome.success()) continue;
                    java.util.Optional<SpatialFeature> discovered = map.spatialFeatures().stream()
                            .filter(feature -> feature.id().equals(outcome.featureId()))
                            .filter(feature -> feature.cells().contains(operation.requestedPath().orderedPositions().get(next)))
                            .findFirst();
                    if (discovered.isPresent()) return commitDetection(map, operation, discovered.get(), next);
                }
                for (SpatialFeature feature : detectionPolicy.candidates(map, operation.playerId(), operation.tokenId(),
                        operation.requestedPath().orderedPositions().get(next))) {
                    java.util.Optional<Boolean> previousCheck = operation.checkOutcome(feature.id());
                    if (previousCheck.isPresent()) {
                        if (previousCheck.get()) return commitDetection(map, operation, feature, next);
                        continue;
                    }
                    MovementCheckRequest request = new MovementCheckRequest(UUID.randomUUID(), operation.operationId(), feature.id(),
                            feature.type(), SpatialTrigger.BECOME_VISIBLE, feature.detectionSpec().ruleReference(),
                            feature.detectionSpec().diceExpression(), feature.detectionSpec().modifier(),
                            feature.detectionSpec().difficulty(), feature.detectionSpec().mode(),
                            MovementCheckOwner.player(operation.playerId()));
                    java.util.Optional<MovementCheckResult> resolved = checkResolver.resolve(request);
                    if (resolved.isEmpty()) {
                        operation.requestCheck(request);
                        operations.save(operation);
                        return response(operation);
                    }
                    operation.requestCheck(request);
                    operation.resumeFromCheck(resolved.get());
                    operations.save(operation);
                    if (resolved.get().success()) return commitDetection(map, operation, feature, next);
                }
                java.util.Optional<MovementInterruption> interruption = interruptionPolicy.beforeEnter(operation,
                        operation.requestedPath().orderedPositions().get(next), map);
                if (interruption.isPresent()) {
                    MovementInterruption value = interruption.get();
                    MovementResolutionResult result = new MovementResolutionResult(operation.requestedPath(), operation.traversedPath(),
                            operation.currentCell(), operation.expectedVersion() + 1, value.publicEvents(), value.reason(),
                            MovementResolutionOutcomeStatus.INTERRUPTED);
                    operation.readyToCommit(result); operations.save(operation);
                    return commitPrepared(map, operation);
                }
                publicEvents.addAll(triggerResolver.resolve(map, SpatialTrigger.LEAVE_CELL, operation.currentCell()));
                map.advancePlayerToken(operation.playerId(), operation.tokenId(), operation.requestedPath().orderedPositions().get(next));
                publicEvents.addAll(resolveNewlyVisibleFeatures(map));
                publicEvents.addAll(triggerResolver.resolve(map, SpatialTrigger.ENTER_CELL,
                        operation.requestedPath().orderedPositions().get(next)));
                operation.advanceTo(next, map.playerTokenPosition(operation.playerId(), operation.tokenId()));
                operations.save(operation);
                if (!publicEvents.isEmpty()) {
                    MovementResolutionResult result = new MovementResolutionResult(operation.requestedPath(), operation.traversedPath(),
                            operation.currentCell(), operation.expectedVersion() + 1, publicEvents,
                            "SPATIAL_FEATURE_TRIGGERED", MovementResolutionOutcomeStatus.INTERRUPTED);
                    operation.readyToCommit(result);
                    operations.save(operation);
                    return commitPrepared(map, operation);
                }
            }
            long committedVersion = operation.expectedVersion() + 1;
            MovementResolutionResult result = new MovementResolutionResult(operation.requestedPath(), operation.traversedPath(), operation.currentCell(), committedVersion, publicEvents, null);
            operation.readyToCommit(result); operations.save(operation);
            return commitPrepared(map, operation);
        } catch (RuntimeException exception) {
            if (exception instanceof MovementOperationConcurrentUpdateException) {
                throw exception;
            }
            if (exception instanceof MovementFinalCommitConflictException) {
                if (operation.status().active()) {
                    operation.cancel(cancelledResult(operation, "MAP_VERSION_CONFLICT"));
                    operations.save(operation);
                }
                throw exception;
            }
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

    private MovementOperationResponse resolveObservation(CombatMap map, MovementResolutionOperation operation) {
        GridPosition cell = operation.currentCell();
        List<SpatialFeature> candidates = detectionPolicy.candidates(map, operation.playerId(), operation.tokenId()).stream()
                .filter(feature -> feature.cells().contains(cell) && feature.triggers().contains(SpatialTrigger.OBSERVE))
                .toList();
        for (SpatialFeature feature : candidates) {
            java.util.Optional<Boolean> previous = operation.checkOutcome(feature.id());
            if (previous.isPresent()) continue;
            MovementCheckRequest request = new MovementCheckRequest(UUID.randomUUID(), operation.operationId(), feature.id(),
                    feature.type(), SpatialTrigger.OBSERVE, feature.detectionSpec().ruleReference(),
                    feature.detectionSpec().diceExpression(), feature.detectionSpec().modifier(),
                    feature.detectionSpec().difficulty(), feature.detectionSpec().mode(),
                    MovementCheckOwner.player(operation.playerId()));
            java.util.Optional<MovementCheckResult> resolved = checkResolver.resolve(request);
            if (resolved.isEmpty()) {
                operation.requestCheck(request);
                operations.save(operation);
                return response(operation);
            }
            operation.requestCheck(request);
            operation.resumeFromCheck(resolved.get());
            operations.save(operation);
        }
        Set<UUID> successful = new java.util.HashSet<>();
        for (MovementCheckOutcome outcome : operation.checkOutcomes()) if (outcome.success()) successful.add(outcome.featureId());
        List<String> publicEvents = triggerResolver.resolveObserved(map, SpatialTrigger.OBSERVE, cell, successful);
        MovementResolutionResult result = new MovementResolutionResult(operation.requestedPath(), operation.traversedPath(),
                cell, operation.expectedVersion() + 1, publicEvents, null, MovementResolutionOutcomeStatus.COMMITTED);
        operation.readyToCommit(result);
        operations.save(operation);
        return commitPrepared(map, operation);
    }
    private MovementOperationResponse commitDetection(CombatMap map, MovementResolutionOperation operation,
            SpatialFeature feature, int next) {
        feature.discover();
        MovementResolutionResult result = new MovementResolutionResult(operation.requestedPath(), operation.traversedPath(),
                operation.currentCell(), operation.expectedVersion() + 1,
                List.of(feature.type().name() + "_DISCOVERED:" + feature.cells().iterator().next().x() + "," + feature.cells().iterator().next().y()),
                "SPATIAL_FEATURE_DISCOVERED", MovementResolutionOutcomeStatus.INTERRUPTED);
        operation.readyToCommit(result);
        operations.save(operation);
        return commitPrepared(map, operation);
    }
    private MovementOperationResponse commitPrepared(CombatMap map, MovementResolutionOperation operation) {
        MovementResolutionResult result = operation.result();
        if (result == null) {
            result = new MovementResolutionResult(operation.requestedPath(), operation.traversedPath(), operation.currentCell(),
                    operation.expectedVersion() + 1, List.of(), null);
        }
        repository.commitMovementResolution(map, operation.expectedVersion() + 1, operation, result);
        if (operation.status() == MovementOperationStatus.READY_TO_COMMIT) {
            operation.committed(result);
            operations.save(operation);
        } else if (operation.status() != MovementOperationStatus.COMMITTED) {
            throw new MovementOperationConcurrentUpdateException();
        }
        return response(operation);
    }

    private void validateStagedPreview(MovementStartRequest request) {
        if (request.path().orderedPositions().isEmpty()) throw new CombatMapMovementPreviewMismatchException();
        MovementPreview preview = preview(new MovementPreviewRequest(request.mapId(), request.playerId(), request.tokenId(),
                request.path().orderedPositions().getLast(), request.waypoints(), request.appliedEdition(), request.expectedVersion()));
        if (!request.previewFingerprint().equals(preview.fingerprint())
                || !request.path().orderedPositions().equals(preview.orderedPositions())
                || request.path().distance() != preview.distance()) {
            throw new CombatMapMovementPreviewMismatchException();
        }
    }
    private static void rebuildStagedMap(CombatMap map, MovementResolutionOperation operation) {
        map.validatePlayerMovement(operation.playerId(), operation.tokenId(), operation.requestedPath(), Integer.MAX_VALUE);
        for (MovementCheckOutcome outcome : operation.checkOutcomes()) {
            if (!outcome.success()) continue;
            map.spatialFeatures().stream().filter(feature -> feature.id().equals(outcome.featureId()))
                    .findFirst().ifPresent(com.dndmaster.combatmap.domain.SpatialFeature::discover);
        }
        var triggerResolver = new com.dndmaster.combatmap.application.spatial.SpatialTriggerResolver();
        for (int index = 1; index <= operation.cursor(); index++) {
            triggerResolver.resolve(map, SpatialTrigger.LEAVE_CELL,
                    operation.requestedPath().orderedPositions().get(index - 1));
            map.advancePlayerToken(operation.playerId(), operation.tokenId(), operation.requestedPath().orderedPositions().get(index));
            resolveNewlyVisibleFeatures(map, triggerResolver);
            triggerResolver.resolve(map, SpatialTrigger.ENTER_CELL,
                    operation.requestedPath().orderedPositions().get(index));
        }
        if (!map.playerTokenPosition(operation.playerId(), operation.tokenId()).equals(operation.currentCell()))
            throw new IllegalStateException("movement reservation cursor does not match its current cell");
    }
    private List<String> resolveNewlyVisibleFeatures(CombatMap map) {
        return resolveNewlyVisibleFeatures(map, triggerResolver);
    }
    private static List<String> resolveNewlyVisibleFeatures(CombatMap map,
            com.dndmaster.combatmap.application.spatial.SpatialTriggerResolver triggerResolver) {
        Set<GridPosition> previouslyVisible = map.visibilitySnapshot() == null
                ? Set.of() : Set.copyOf(map.visibilitySnapshot().current());
        map.refreshVisibility(map.visibilitySnapshot() == null ? 0 : map.visibilitySnapshot().ruleTurn());
        List<GridPosition> newlyVisible = map.visibilitySnapshot().current().stream()
                .filter(cell -> !previouslyVisible.contains(cell)).toList();
        return triggerResolver.resolveVisible(map, newlyVisible);
    }
    private static void requireMap(MovementResolutionOperation operation, MapId mapId) {
        if (!operation.mapId().equals(mapId)) throw new IllegalArgumentException("movement reservation does not belong to this map");
    }
    private static boolean retryable(RuntimeException exception) {
        return exception instanceof com.dndmaster.combatmap.infrastructure.persistence.CombatMapPersistenceException;
    }
    private static MovementResolutionResult cancelledResult(MovementResolutionOperation operation, String reason) {
        return new MovementResolutionResult(operation.requestedPath(), operation.traversedPath(), operation.currentCell(),
                operation.expectedVersion(), List.of(), reason, MovementResolutionOutcomeStatus.CANCELLED);
    }
    private static MovementOperationResponse response(MovementResolutionOperation operation) {
        MovementResolutionResult result = operation.result();
        if (result == null) {
            MovementResolutionOutcomeStatus outcome = switch (operation.status()) {
                case PREPARING, CHECK_PENDING, READY_TO_COMMIT -> MovementResolutionOutcomeStatus.CHECK_REQUIRED;
                case RETRY_WAIT -> MovementResolutionOutcomeStatus.RETRY_REQUIRED;
                case COMMITTED -> MovementResolutionOutcomeStatus.COMMITTED;
                case CANCELLED -> MovementResolutionOutcomeStatus.CANCELLED;
            };
            result = new MovementResolutionResult(operation.requestedPath(), operation.traversedPath(),
                    operation.currentCell(), operation.expectedVersion(), List.of(), null, outcome);
        }
        return new MovementOperationResponse(operation.operationId(), operation.status(), result,
                operation.pendingCheck() == null ? null : operation.pendingCheck().playerView(), operation.pendingCheck());
    }
    private static final class UnsupportedOperationRepository implements MovementResolutionOperationRepository {
        private IllegalStateException unsupported() { return new IllegalStateException("movement reservations require an operation repository"); }
        public java.util.Optional<MovementResolutionOperation> findById(UUID id) { throw unsupported(); } public java.util.Optional<MovementResolutionOperation> findOperationByCommandId(UUID id) { throw unsupported(); }
        public java.util.Optional<MovementResolutionOperation> findActiveByMapId(com.dndmaster.combatmap.domain.MapId id) { throw unsupported(); } public MovementResolutionOperation reserve(MovementResolutionOperation operation) { throw unsupported(); } public void save(MovementResolutionOperation operation) { throw unsupported(); } public java.util.List<MovementResolutionOperation> findRecoverable() { throw unsupported(); }
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
