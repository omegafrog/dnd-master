package com.dndmaster.character.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(name = "characterApiContractExceptionHandler")
public final class ApiContractExceptionHandler {
    @ExceptionHandler(ApiRequestGuard.ApiContractException.class)
    ResponseEntity<ErrorResponse> handle(ApiRequestGuard.ApiContractException exception) {
        return ResponseEntity.status(exception.status()).body(new ErrorResponse(exception.code()));
    }
    record ErrorResponse(String code) {}
}
