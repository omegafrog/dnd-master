package com.dndmaster.combatmap.api;

import com.dndmaster.combatmap.application.view.MapPlacementRequiredException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(name = "combatMapApiContractExceptionHandler")
public final class ApiContractExceptionHandler {
    @ExceptionHandler(ApiRequestGuard.ApiContractException.class)
    ResponseEntity<ErrorResponse> handle(ApiRequestGuard.ApiContractException exception) {
        return ResponseEntity.status(exception.status()).body(new ErrorResponse(exception.code()));
    }

    @ExceptionHandler(MapPlacementRequiredException.class)
    ResponseEntity<SpawnRequiredResponse> handle(MapPlacementRequiredException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new SpawnRequiredResponse("MAP_SPAWN_REVIEW_REQUIRED", "맵 시작 위치를 확인해야 모험을 시작할 수 있습니다."));
    }

    record ErrorResponse(String code) {}
    record SpawnRequiredResponse(String error, String message) {}
}
