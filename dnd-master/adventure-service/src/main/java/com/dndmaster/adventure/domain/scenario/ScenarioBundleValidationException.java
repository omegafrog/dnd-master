package com.dndmaster.adventure.domain.scenario;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public final class ScenarioBundleValidationException extends IllegalStateException {
    public ScenarioBundleValidationException(String message) {
        super(message);
    }
}
