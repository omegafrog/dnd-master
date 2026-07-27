package com.dndmaster.adventure.domain.scenario;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.FORBIDDEN)
public final class ScenarioAccessDeniedException extends RuntimeException {
    public ScenarioAccessDeniedException() {
        super("scenario is not owned by requesting player");
    }
}
