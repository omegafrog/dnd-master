package com.dndmaster.adventure.api;

import com.dndmaster.adventure.application.campaign.CampaignPlanPreparationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public final class CampaignPlanExceptionHandler {
    @ExceptionHandler(CampaignPlanPreparationException.class)
    public ResponseEntity<PreparationError> handle(CampaignPlanPreparationException exception) {
        HttpStatus status = switch (exception.code()) {
            case SESSION_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case SESSION_ACCESS_DENIED -> HttpStatus.FORBIDDEN;
            default -> HttpStatus.UNPROCESSABLE_ENTITY;
        };
        return ResponseEntity.status(status)
                .body(new PreparationError(exception.code().name(), exception.getMessage()));
    }

    public record PreparationError(String code, String message) {}
}
