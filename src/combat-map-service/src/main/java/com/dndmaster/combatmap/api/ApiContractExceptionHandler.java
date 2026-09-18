package com.dndmaster.combatmap.api;

import com.dndmaster.combatmap.application.view.MapPlacementRequiredException;
import com.dndmaster.combatmap.application.spatial.SpatialPreparationCommandConflictException;
import com.dndmaster.combatmap.application.spatial.SpatialPreparationVersionConflictException;
import com.dndmaster.combatmap.domain.CombatMapMovementDeniedException;
import com.dndmaster.combatmap.application.movement.CombatMapMovementStaleException;
import com.dndmaster.combatmap.application.movement.CombatMapMovementPreviewMismatchException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.dndmaster.combatmap.application.movement.MovementReservationConflictException;
import com.dndmaster.combatmap.application.movement.MovementVersionConflictException;
import com.dndmaster.combatmap.application.movement.MovementOperationConcurrentUpdateException;
import com.dndmaster.combatmap.application.movement.MovementCommandConflictException;

@RestControllerAdvice(name = "combatMapApiContractExceptionHandler")
public final class ApiContractExceptionHandler {
    @ExceptionHandler(MovementReservationConflictException.class)
    ResponseEntity<ErrorResponse> handle(MovementReservationConflictException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse("MAP_MUTATION_IN_PROGRESS"));
    }

    @ExceptionHandler(MovementVersionConflictException.class)
    ResponseEntity<ErrorResponse> handle(MovementVersionConflictException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse("STALE_MOVEMENT_PROPOSAL"));
    }

    @ExceptionHandler(MovementOperationConcurrentUpdateException.class)
    ResponseEntity<ErrorResponse> handle(MovementOperationConcurrentUpdateException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse("MOVEMENT_OPERATION_IN_PROGRESS"));
    }

    @ExceptionHandler(MovementCommandConflictException.class)
    ResponseEntity<ErrorResponse> handle(MovementCommandConflictException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse("MOVEMENT_COMMAND_CONFLICT"));
    }
    @ExceptionHandler(ApiRequestGuard.ApiContractException.class)
    ResponseEntity<ErrorResponse> handle(ApiRequestGuard.ApiContractException exception) {
        return ResponseEntity.status(exception.status()).body(new ErrorResponse(exception.code()));
    }

    @ExceptionHandler(MapPlacementRequiredException.class)
    ResponseEntity<SpawnRequiredResponse> handle(MapPlacementRequiredException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new SpawnRequiredResponse("MAP_SPAWN_REVIEW_REQUIRED", "맵 시작 위치를 확인해야 모험을 시작할 수 있습니다."));
    }

    @ExceptionHandler(SpatialPreparationCommandConflictException.class)
    ResponseEntity<ErrorResponse> handle(SpatialPreparationCommandConflictException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse("SPATIAL_PREPARATION_COMMAND_CONFLICT"));
    }

    @ExceptionHandler(SpatialPreparationVersionConflictException.class)
    ResponseEntity<ErrorResponse> handle(SpatialPreparationVersionConflictException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse("SPATIAL_PREPARATION_VERSION_CONFLICT"));
    }

    @ExceptionHandler(CombatMapMovementDeniedException.class)
    ResponseEntity<ErrorResponse> handle(CombatMapMovementDeniedException exception) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(new ErrorResponse("MOVEMENT_NOT_ALLOWED"));
    }

    @ExceptionHandler(CombatMapMovementStaleException.class)
    ResponseEntity<ErrorResponse> handle(CombatMapMovementStaleException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse("STALE_MOVEMENT_PROPOSAL"));
    }

    @ExceptionHandler(CombatMapMovementPreviewMismatchException.class)
    ResponseEntity<ErrorResponse> handle(CombatMapMovementPreviewMismatchException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse("MOVEMENT_PREVIEW_MISMATCH"));
    }

    record ErrorResponse(String code) {}
    record SpawnRequiredResponse(String error, String message) {}
}
