package com.dndmaster.adventure.application.combat;

import com.fasterxml.jackson.annotation.JsonIgnore;

public record CombatMapMoveResult(long version, java.util.UUID operationId, CombatMapMovementStatus status,
        java.util.List<CombatMapPreviewPosition> requestedPath, java.util.List<CombatMapPreviewPosition> traversedPath, CombatMapPreviewPosition finalPosition,
        java.util.List<String> publicEvents, String interruptionReason, CombatMapPendingCheck pendingCheck,
        MovementFollowUpCommand followUp, @JsonIgnore CombatMapCheckDetails pendingCheckDetails,
        java.util.UUID hostileTokenId) {
    public CombatMapMoveResult(long version, java.util.UUID operationId, CombatMapMovementStatus status,
            java.util.List<CombatMapPreviewPosition> requestedPath, java.util.List<CombatMapPreviewPosition> traversedPath,
            CombatMapPreviewPosition finalPosition, java.util.List<String> publicEvents, String interruptionReason) {
        this(version, operationId, status, requestedPath, traversedPath, finalPosition, publicEvents, interruptionReason, null, null, null, null);
    }
    public CombatMapMoveResult {
        if (version < 0) throw new IllegalArgumentException("map version must be non-negative");
        traversedPath = traversedPath == null ? java.util.List.of() : java.util.List.copyOf(traversedPath);
        publicEvents = publicEvents == null ? java.util.List.of() : java.util.List.copyOf(publicEvents);
        requestedPath = requestedPath == null ? java.util.List.of() : java.util.List.copyOf(requestedPath);
    }
    public CombatMapMoveResult(long version, java.util.UUID operationId, CombatMapMovementStatus status,
            java.util.List<CombatMapPreviewPosition> requestedPath, java.util.List<CombatMapPreviewPosition> traversedPath,
            CombatMapPreviewPosition finalPosition, java.util.List<String> publicEvents, String interruptionReason,
            CombatMapPendingCheck pendingCheck) {
        this(version, operationId, status, requestedPath, traversedPath, finalPosition, publicEvents,
                interruptionReason, pendingCheck, null, null, null);
    }
    public CombatMapMoveResult(long version, java.util.UUID operationId, CombatMapMovementStatus status,
            java.util.List<CombatMapPreviewPosition> requestedPath, java.util.List<CombatMapPreviewPosition> traversedPath,
            CombatMapPreviewPosition finalPosition, java.util.List<String> publicEvents, String interruptionReason,
            CombatMapPendingCheck pendingCheck, CombatMapCheckDetails pendingCheckDetails) {
        this(version, operationId, status, requestedPath, traversedPath, finalPosition, publicEvents,
                interruptionReason, pendingCheck, null, pendingCheckDetails, null);
    }
    public CombatMapMoveResult(long version, java.util.UUID operationId, CombatMapMovementStatus status,
            java.util.List<CombatMapPreviewPosition> requestedPath, java.util.List<CombatMapPreviewPosition> traversedPath,
            CombatMapPreviewPosition finalPosition, java.util.List<String> publicEvents, String interruptionReason,
            CombatMapPendingCheck pendingCheck, MovementFollowUpCommand followUp, CombatMapCheckDetails pendingCheckDetails) {
        this(version, operationId, status, requestedPath, traversedPath, finalPosition, publicEvents, interruptionReason,
                pendingCheck, followUp, pendingCheckDetails, null);
    }
    public CombatMapMoveResult(long version) { this(version, null, CombatMapMovementStatus.COMMITTED, java.util.List.of(), java.util.List.of(), null, java.util.List.of(), null, null, null, null, null); }

    public CombatMapMoveResult(long version, java.util.UUID operationId, CombatMapMovementStatus status,
            java.util.List<CombatMapPreviewPosition> traversedPath, CombatMapPreviewPosition finalPosition,
            java.util.List<String> publicEvents, String interruptionReason) {
        this(version, operationId, status, java.util.List.of(), traversedPath, finalPosition, publicEvents, interruptionReason, null, null, null, null);
    }

    public CombatMapMoveResult withFollowUp(MovementFollowUpCommand value) {
        return new CombatMapMoveResult(version, operationId, status, requestedPath, traversedPath, finalPosition,
                publicEvents, interruptionReason, pendingCheck, value, pendingCheckDetails, hostileTokenId);
    }

    public CombatMapMoveResult withHostileTokenId(java.util.UUID value) {
        return new CombatMapMoveResult(version, operationId, status, requestedPath, traversedPath, finalPosition,
                publicEvents, interruptionReason, pendingCheck, followUp, pendingCheckDetails, value);
    }
}
