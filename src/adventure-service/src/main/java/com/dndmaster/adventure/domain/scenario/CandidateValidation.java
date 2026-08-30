package com.dndmaster.adventure.domain.scenario;

import java.util.Objects;

public record CandidateValidation(String code, String message, CandidateRecoverability recoverability) {
    public CandidateValidation {
        if (code == null || code.isBlank()) throw new IllegalArgumentException("validation code must not be blank");
        if (message == null || message.isBlank()) throw new IllegalArgumentException("validation message must not be blank");
        recoverability = Objects.requireNonNull(recoverability, "recoverability must not be null");
    }
}
