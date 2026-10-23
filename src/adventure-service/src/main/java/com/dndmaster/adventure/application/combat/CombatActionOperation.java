package com.dndmaster.adventure.application.combat;

import com.dndmaster.adventure.domain.combat.TurnResourceCost;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Durable application saga record. The command identity is the idempotency boundary. */
public final class CombatActionOperation {
    public enum Status { RESERVED, GM_INTERVENTION_PENDING, PROCESSING_FAILED, COMMITTED }

    private final UUID commandId;
    private final String fingerprint;
    private final UUID encounterId;
    private final UUID actorId;
    private final TurnResourceCost reservedCost;
    private final List<CombatActionStep> steps;
    private Status status;
    private CombatActionResponse response;
    private String failure;
    private Integer diceTotal;
    private MovementFollowUpCommand movementFollowUp;

    public CombatActionOperation(UUID commandId, String fingerprint, UUID encounterId, UUID actorId,
                                 TurnResourceCost reservedCost, List<CombatActionStep> steps) {
        this.commandId = Objects.requireNonNull(commandId);
        this.fingerprint = Objects.requireNonNull(fingerprint);
        this.encounterId = Objects.requireNonNull(encounterId);
        this.actorId = Objects.requireNonNull(actorId);
        this.reservedCost = Objects.requireNonNull(reservedCost);
        this.steps = new java.util.ArrayList<>(steps);
        this.status = Status.RESERVED;
    }

    public static CombatActionOperation restore(UUID commandId, String fingerprint, UUID encounterId, UUID actorId,
                                                TurnResourceCost reservedCost, List<CombatActionStep> steps,
                                                Status status, CombatActionResponse response, String failure) {
        CombatActionOperation operation = new CombatActionOperation(commandId, fingerprint, encounterId, actorId,
                reservedCost, steps);
        operation.status = status;
        operation.response = response;
        operation.failure = failure;
        return operation;
    }

    public static CombatActionOperation restore(UUID commandId, String fingerprint, UUID encounterId, UUID actorId,
            TurnResourceCost reservedCost, List<CombatActionStep> steps, Status status,
            CombatActionResponse response, String failure, MovementFollowUpCommand movementFollowUp) {
        CombatActionOperation operation = restore(commandId, fingerprint, encounterId, actorId, reservedCost,
                steps, status, response, failure);
        operation.movementFollowUp = movementFollowUp;
        return operation;
    }

    public void requireSame(String candidateFingerprint) {
        if (!fingerprint.equals(candidateFingerprint)) throw new CombatIdempotencyConflictException();
    }

    public void failed(Throwable cause) {
        if (status != Status.GM_INTERVENTION_PENDING) status = Status.PROCESSING_FAILED;
        failure = cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }

    public void failedBeforeCommit(Throwable cause) {
        failed(cause);
        response = null;
    }

    public void committed(CombatActionResponse result) {
        response = Objects.requireNonNull(result);
        status = Status.COMMITTED;
        failure = null;
    }

    public void awaitingMovementAdjudication(CombatActionResponse result, MovementFollowUpCommand followUp) {
        awaitingMovementAdjudication(result);
        movementFollowUp = Objects.requireNonNull(followUp);
    }

    public void awaitingMovementAdjudication(CombatActionResponse result) {
        response = Objects.requireNonNull(result);
        status = Status.GM_INTERVENTION_PENDING;
        failure = null;
    }

    public void recordDiceTotal(int total) {
        if (diceTotal != null && diceTotal != total) throw new IllegalStateException("movement roll already recorded");
        diceTotal = total;
    }

    public void completeStep(String name) {
        replaceStep(name, CombatActionStep.Status.DONE);
    }

    public void failStep(String name) {
        replaceStep(name, CombatActionStep.Status.FAILED);
    }

    private void replaceStep(String name, CombatActionStep.Status nextStatus) {
        for (int index = 0; index < steps.size(); index++) {
            CombatActionStep step = steps.get(index);
            if (step.name().equals(name)) {
                steps.set(index, new CombatActionStep(step.name(), step.idempotencyKey(), nextStatus));
                return;
            }
        }
    }

    public UUID commandId() { return commandId; }
    public String fingerprint() { return fingerprint; }
    public UUID encounterId() { return encounterId; }
    public UUID actorId() { return actorId; }
    public TurnResourceCost reservedCost() { return reservedCost; }
    public List<CombatActionStep> steps() { return List.copyOf(steps); }
    public Status status() { return status; }
    public CombatActionResponse response() { return response; }
    public String failure() { return failure; }
    public Integer diceTotal() { return diceTotal; }
    public MovementFollowUpCommand movementFollowUp() { return movementFollowUp; }
    public CombatActionStep step(String name) {
        return steps.stream().filter(step -> step.name().equals(name)).findFirst().orElse(null);
    }
    public UUID mapOperationId() {
        CombatActionStep map = step("map");
        if (map == null) throw new IllegalStateException("combat action has no durable map operation");
        try {
            return UUID.fromString(map.idempotencyKey());
        } catch (IllegalArgumentException invalid) {
            throw new IllegalStateException("durable map operation identity is invalid", invalid);
        }
    }
}
