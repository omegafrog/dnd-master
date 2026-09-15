package com.dndmaster.relay.api;

import java.util.Map;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public final class RelayExceptionHandler {
    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<Map<String, String>> responseStatus(ResponseStatusException failure) {
        return ResponseEntity.status(failure.getStatusCode()).body(Map.of("code", "UNAUTHORIZED"));
    }
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, String>> invalidRequest(IllegalArgumentException failure) {
        return ResponseEntity.badRequest().body(Map.of("code", "INVALID_REQUEST"));
    }
}
