package com.dndmaster.adventure.application.scenario;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
public final class LegacyScenarioIngestionValidationException extends RuntimeException {
    public LegacyScenarioIngestionValidationException(String message) {
        super(message);
    }
}
