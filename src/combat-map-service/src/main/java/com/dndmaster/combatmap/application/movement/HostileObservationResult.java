package com.dndmaster.combatmap.application.movement;

import com.dndmaster.combatmap.domain.TokenId;
import java.util.Objects;
import java.util.Optional;

public record HostileObservationResult(Status status, TokenId hostileTokenId,
        Optional<MovementCheckRequest> check, Optional<MovementInterruption> interruption) {
    public enum Status { NO_OBSERVATION, NEW, CONTINUOUS, REACQUIRED, CHECK_REQUIRED }

    public HostileObservationResult {
        status = Objects.requireNonNull(status, "hostile observation status must not be null");
        check = check == null ? Optional.empty() : check;
        interruption = interruption == null ? Optional.empty() : interruption;
        if (status == Status.NO_OBSERVATION || status == Status.CONTINUOUS) {
            if (interruption.isPresent()) throw new IllegalArgumentException("non-stopping observation cannot interrupt movement");
        }
    }

    public static HostileObservationResult none() {
        return new HostileObservationResult(Status.NO_OBSERVATION, null, Optional.empty(), Optional.empty());
    }
}
