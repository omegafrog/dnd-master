package com.dndmaster.identityaccess.api;

import com.dndmaster.identityaccess.infrastructure.security.UsernameAlreadyExistsException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class AuthenticationErrorHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, String>> invalidRequest(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
    }

    @ExceptionHandler(BadCredentialsException.class)
    ResponseEntity<Map<String, String>> badCredentials() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "invalid credentials"));
    }

    @ExceptionHandler(UsernameAlreadyExistsException.class)
    ResponseEntity<Map<String, String>> usernameConflict() {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "username already exists"));
    }
}
