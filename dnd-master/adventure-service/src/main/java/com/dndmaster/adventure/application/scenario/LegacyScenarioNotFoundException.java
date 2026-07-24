package com.dndmaster.adventure.application.scenario;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public final class LegacyScenarioNotFoundException extends RuntimeException {
    public LegacyScenarioNotFoundException() {
        super("legacy scenario not found");
    }
}
