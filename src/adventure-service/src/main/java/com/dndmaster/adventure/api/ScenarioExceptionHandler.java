package com.dndmaster.adventure.api;

import com.dndmaster.adventure.domain.scenario.ScenarioAccessDeniedException;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleAccessDeniedException;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleNotFoundException;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public final class ScenarioExceptionHandler {
    @ExceptionHandler({ScenarioAccessDeniedException.class, ScenarioBundleAccessDeniedException.class})
    public ResponseEntity<Void> accessDenied(RuntimeException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    @ExceptionHandler(ScenarioBundleNotFoundException.class)
    public ResponseEntity<Void> notFound(ScenarioBundleNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    @ExceptionHandler(ScenarioBundleValidationException.class)
    public ResponseEntity<Void> validation(ScenarioBundleValidationException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
    }
}
