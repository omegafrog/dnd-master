package com.dndmaster.adventure.domain.scenario;

import java.util.Objects;
import java.util.Optional;

public final class AdventureScenario {
    private final ScenarioId id;
    private final OwnerPlayerId ownerPlayerId;
    private final ScenarioSource source;
    private ScenarioPreparationStatus status;
    private String failureReason;

    private AdventureScenario(ScenarioId id, OwnerPlayerId ownerPlayerId, ScenarioSource source) {
        this.id = Objects.requireNonNull(id, "scenario id must not be null");
        this.ownerPlayerId = Objects.requireNonNull(ownerPlayerId, "owner player id must not be null");
        this.source = Objects.requireNonNull(source, "scenario source must not be null");
        this.status = ScenarioPreparationStatus.UPLOADED;
    }

    public static AdventureScenario recordUpload(
            ScenarioId id, OwnerPlayerId ownerPlayerId, ScenarioSource source) {
        return new AdventureScenario(id, ownerPlayerId, source);
    }

    public void recordPreparationSuccess() {
        if (status != ScenarioPreparationStatus.UPLOADED) {
            throw new IllegalStateException("only uploaded scenario can become ready");
        }
        status = ScenarioPreparationStatus.READY;
        failureReason = null;
    }

    public void recordPreparationFailure(String reason) {
        if (status != ScenarioPreparationStatus.UPLOADED) {
            throw new IllegalStateException("only uploaded scenario can fail preparation");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("failure reason must not be blank");
        }
        status = ScenarioPreparationStatus.FAILED;
        failureReason = reason.trim();
    }

    public void authorizeAccess(RequestingPlayerId requestingPlayerId) {
        Objects.requireNonNull(requestingPlayerId, "requesting player id must not be null");
        if (!ownerPlayerId.value().equals(requestingPlayerId.value())) {
            throw new ScenarioAccessDeniedException();
        }
    }

    public boolean isUsableByAiGameMaster() {
        return status == ScenarioPreparationStatus.READY;
    }

    public ScenarioId id() {
        return id;
    }

    public OwnerPlayerId ownerPlayerId() {
        return ownerPlayerId;
    }

    public ScenarioSource source() {
        return source;
    }

    public ScenarioPreparationStatus status() {
        return status;
    }

    public Optional<String> failureReason() {
        return Optional.ofNullable(failureReason);
    }
}
