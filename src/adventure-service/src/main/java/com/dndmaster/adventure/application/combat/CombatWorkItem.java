package com.dndmaster.adventure.application.combat;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Durable unit of AI progression. The lease is the delivery/claim boundary. */
public final class CombatWorkItem {
    public enum WorkType { AI_TURN, ENEMY_SHEET_PREPARATION }
    public enum Status { PENDING, CLAIMED, COMPLETED, FAILED }

    private final UUID workItemId;
    private final UUID encounterId;
    private final UUID operationId;
    private final long expectedEncounterVersion;
    private final WorkType workType;
    private final Instant dueAt;
    private final int attemptCount;
    private final Status status;
    private final UUID leaseToken;
    private final Instant leaseUntil;
    private final String failure;
    private final AiTacticalInstructionContext tacticalInstruction;
    private final CombatActionCommand command;
    private final int completedSteps;
    private final UUID aiRequestId;
    private final EnemySheetPreparationRequest enemySheetPreparationRequest;
    private final AiTurnPlan decisionPlan;

    public CombatWorkItem(UUID workItemId, UUID encounterId, UUID operationId, long expectedEncounterVersion,
                          WorkType workType, Instant dueAt, int attemptCount,
                          AiTacticalInstructionContext tacticalInstruction) {
        this(workItemId, encounterId, operationId, expectedEncounterVersion, workType, dueAt, attemptCount,
                Status.PENDING, null, null, null, tacticalInstruction, null, 0, null, null, null);
    }

    public CombatWorkItem(UUID workItemId, UUID encounterId, UUID operationId, long expectedEncounterVersion,
                          WorkType workType, Instant dueAt, int attemptCount,
                          AiTacticalInstructionContext tacticalInstruction, CombatActionCommand command) {
        this(workItemId, encounterId, operationId, expectedEncounterVersion, workType, dueAt, attemptCount,
                tacticalInstruction, command, 0);
    }

    public CombatWorkItem(UUID workItemId, UUID encounterId, UUID operationId, long expectedEncounterVersion,
                          WorkType workType, Instant dueAt, int attemptCount,
                          AiTacticalInstructionContext tacticalInstruction, CombatActionCommand command,
                          int completedSteps) {
        this(workItemId, encounterId, operationId, expectedEncounterVersion, workType, dueAt, attemptCount,
                Status.PENDING, null, null, null, tacticalInstruction, command, completedSteps, null, null, null);
    }

    public CombatWorkItem(UUID workItemId, UUID encounterId, UUID operationId, long expectedEncounterVersion,
                          WorkType workType, Instant dueAt, int attemptCount,
                          AiTacticalInstructionContext tacticalInstruction, CombatActionCommand command,
                          int completedSteps, UUID aiRequestId) {
        this(workItemId, encounterId, operationId, expectedEncounterVersion, workType, dueAt, attemptCount,
                Status.PENDING, null, null, null, tacticalInstruction, command, completedSteps, aiRequestId, null, null);
    }

    public static CombatWorkItem restore(UUID workItemId, UUID encounterId, UUID operationId,
                                         long expectedEncounterVersion, WorkType workType, Instant dueAt,
                                         int attemptCount, Status status, UUID leaseToken, Instant leaseUntil,
                                         String failure, AiTacticalInstructionContext tacticalInstruction,
                                         CombatActionCommand command, int completedSteps) {
        return new CombatWorkItem(workItemId, encounterId, operationId, expectedEncounterVersion, workType, dueAt,
                attemptCount, status, leaseToken, leaseUntil, failure, tacticalInstruction, command, completedSteps,
                null, null, null);
    }

    public static CombatWorkItem restore(UUID workItemId, UUID encounterId, UUID operationId,
                                         long expectedEncounterVersion, WorkType workType, Instant dueAt,
                                         int attemptCount, Status status, UUID leaseToken, Instant leaseUntil,
                                         String failure, AiTacticalInstructionContext tacticalInstruction,
                                         CombatActionCommand command, int completedSteps, UUID aiRequestId) {
        return restore(workItemId, encounterId, operationId, expectedEncounterVersion, workType, dueAt, attemptCount,
                status, leaseToken, leaseUntil, failure, tacticalInstruction, command, completedSteps, aiRequestId, null);
    }

    public static CombatWorkItem restore(UUID workItemId, UUID encounterId, UUID operationId,
                                         long expectedEncounterVersion, WorkType workType, Instant dueAt,
                                         int attemptCount, Status status, UUID leaseToken, Instant leaseUntil,
                                         String failure, AiTacticalInstructionContext tacticalInstruction,
                                         CombatActionCommand command, int completedSteps, UUID aiRequestId,
                                         EnemySheetPreparationRequest preparationRequest) {
        return new CombatWorkItem(workItemId, encounterId, operationId, expectedEncounterVersion, workType, dueAt,
                attemptCount, status, leaseToken, leaseUntil, failure, tacticalInstruction, command, completedSteps,
                aiRequestId, preparationRequest, null);
    }

    public static CombatWorkItem restore(UUID workItemId, UUID encounterId, UUID operationId,
                                         long expectedEncounterVersion, WorkType workType, Instant dueAt,
                                         int attemptCount, Status status, UUID leaseToken, Instant leaseUntil,
                                         String failure, AiTacticalInstructionContext tacticalInstruction,
                                         CombatActionCommand command, int completedSteps, UUID aiRequestId,
                                         EnemySheetPreparationRequest preparationRequest, AiTurnPlan decisionPlan) {
        return new CombatWorkItem(workItemId, encounterId, operationId, expectedEncounterVersion, workType, dueAt,
                attemptCount, status, leaseToken, leaseUntil, failure, tacticalInstruction, command, completedSteps,
                aiRequestId, preparationRequest, decisionPlan);
    }

    public static CombatWorkItem enemySheetPreparation(UUID workItemId, UUID encounterId, long expectedVersion,
                                                        Instant dueAt, EnemySheetPreparationRequest request) {
        return enemySheetPreparation(workItemId, encounterId, expectedVersion, dueAt, request, null);
    }

    public static CombatWorkItem enemySheetPreparation(UUID workItemId, UUID encounterId, long expectedVersion,
                                                        Instant dueAt, EnemySheetPreparationRequest request,
                                                        CombatActionCommand firstAiTurnTemplate) {
        return new CombatWorkItem(workItemId, encounterId, null, expectedVersion, WorkType.ENEMY_SHEET_PREPARATION,
                dueAt, 0, Status.PENDING, null, null, null, AiTacticalInstructionContext.none(), firstAiTurnTemplate,
                0, request.sourceTurnId(), request, null);
    }

    private CombatWorkItem(UUID workItemId, UUID encounterId, UUID operationId, long expectedEncounterVersion,
                           WorkType workType, Instant dueAt, int attemptCount, Status status,
                           UUID leaseToken, Instant leaseUntil, String failure,
                           AiTacticalInstructionContext tacticalInstruction, CombatActionCommand command,
                           int completedSteps, UUID aiRequestId, EnemySheetPreparationRequest preparationRequest,
                           AiTurnPlan decisionPlan) {
        this.workItemId = Objects.requireNonNull(workItemId);
        this.encounterId = Objects.requireNonNull(encounterId);
        if (expectedEncounterVersion < 0 || attemptCount < 0 || completedSteps < 0) throw new IllegalArgumentException("invalid work item counters");
        this.operationId = operationId;
        this.expectedEncounterVersion = expectedEncounterVersion;
        this.workType = Objects.requireNonNull(workType);
        this.dueAt = Objects.requireNonNull(dueAt);
        this.attemptCount = attemptCount;
        this.status = Objects.requireNonNull(status);
        this.leaseToken = leaseToken;
        this.leaseUntil = leaseUntil;
        this.failure = failure;
        this.tacticalInstruction = tacticalInstruction == null ? AiTacticalInstructionContext.none() : tacticalInstruction;
        this.command = command;
        this.completedSteps = completedSteps;
        this.aiRequestId = aiRequestId;
        this.enemySheetPreparationRequest = preparationRequest;
        this.decisionPlan = decisionPlan;
    }

    public CombatWorkItem claimed(UUID token, Instant until) {
        return copy(Status.CLAIMED, token, until, failure,
                attemptCount == Integer.MAX_VALUE ? Integer.MAX_VALUE : attemptCount + 1,
                command, completedSteps, operationId, decisionPlan);
    }

    public CombatWorkItem withCommand(CombatActionCommand value) {
        return copy(status, leaseToken, leaseUntil, failure, attemptCount, value, completedSteps, operationId, decisionPlan);
    }

    public CombatWorkItem withDecisionPlan(AiTurnPlan value) {
        return copy(status, leaseToken, leaseUntil, failure, attemptCount, command, completedSteps, operationId,
                Objects.requireNonNull(value));
    }

    public CombatWorkItem withNextOperation(UUID nextOperationId, CombatActionCommand nextCommand) {
        return copy(status, leaseToken, leaseUntil, failure, attemptCount, nextCommand, completedSteps + 1, nextOperationId, null);
    }

    public CombatWorkItem completed(UUID token) {
        requireLease(token);
        return copy(Status.COMPLETED, null, null, null, attemptCount, command, completedSteps, operationId, decisionPlan);
    }

    public CombatWorkItem retry(UUID token, Instant nextDueAt, String reason) {
        requireLease(token);
        return new CombatWorkItem(workItemId, encounterId, operationId, expectedEncounterVersion, workType,
                nextDueAt, attemptCount, Status.PENDING, null, null, reason, tacticalInstruction, command,
                completedSteps, aiRequestId, enemySheetPreparationRequest, decisionPlan);
    }

    public CombatWorkItem defer(UUID token, Instant nextDueAt) {
        requireLease(token);
        return new CombatWorkItem(workItemId, encounterId, operationId, expectedEncounterVersion, workType,
                nextDueAt, attemptCount - 1, Status.PENDING, null, null, failure, tacticalInstruction,
                command, completedSteps, aiRequestId, enemySheetPreparationRequest, decisionPlan);
    }

    public CombatWorkItem manualRetry(Instant nextDueAt) {
        return manualRetry(nextDueAt, aiRequestId);
    }

    public CombatWorkItem manualRetry(Instant nextDueAt, UUID nextAiRequestId) {
        if (status != Status.FAILED) throw new IllegalStateException("combat work item is not failed");
        return new CombatWorkItem(workItemId, encounterId, operationId, expectedEncounterVersion, workType,
                nextDueAt, 0, Status.PENDING, null, null, null, tacticalInstruction, command,
                completedSteps, nextAiRequestId, enemySheetPreparationRequest, decisionPlan);
    }

    public CombatWorkItem failed(UUID token, String reason) {
        requireLease(token);
        return copy(Status.FAILED, null, null, reason, attemptCount, command, completedSteps, operationId, decisionPlan);
    }

    private CombatWorkItem copy(Status nextStatus, UUID token, Instant until, String reason, int attempts,
                                CombatActionCommand nextCommand, int nextCompletedSteps, UUID nextOperationId,
                                AiTurnPlan nextDecisionPlan) {
        return new CombatWorkItem(workItemId, encounterId, nextOperationId, expectedEncounterVersion, workType, dueAt,
                attempts, nextStatus, token, until, reason, tacticalInstruction, nextCommand, nextCompletedSteps,
                aiRequestId, enemySheetPreparationRequest, nextDecisionPlan);
    }

    private void requireLease(UUID token) {
        if (status != Status.CLAIMED || !Objects.equals(leaseToken, token)) throw new IllegalStateException("combat work lease mismatch");
    }

    public UUID workItemId() { return workItemId; }
    public UUID encounterId() { return encounterId; }
    public UUID operationId() { return operationId; }
    public long expectedEncounterVersion() { return expectedEncounterVersion; }
    public WorkType workType() { return workType; }
    public Instant dueAt() { return dueAt; }
    public int attemptCount() { return attemptCount; }
    public Status status() { return status; }
    public UUID leaseToken() { return leaseToken; }
    public Instant leaseUntil() { return leaseUntil; }
    public String failure() { return failure; }
    public AiTacticalInstructionContext tacticalInstruction() { return tacticalInstruction; }
    public CombatActionCommand command() { return command; }
    public int completedSteps() { return completedSteps; }
    public UUID aiRequestId() { return aiRequestId; }
    public EnemySheetPreparationRequest enemySheetPreparationRequest() { return enemySheetPreparationRequest; }
    public AiTurnPlan decisionPlan() { return decisionPlan; }
    public String playerVisibleFailure() {
        if (status != Status.FAILED) return null;
        return workType == WorkType.ENEMY_SHEET_PREPARATION
                ? "COMBAT_PREPARATION_BLOCKED" : "COMBAT_PROCESSING_FAILED";
    }
}
