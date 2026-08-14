package com.dndmaster.adventure.api;

import com.dndmaster.adventure.domain.scenario.ScenarioAccessDeniedException;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleAccessDeniedException;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleNotFoundException;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleValidationException;
import com.dndmaster.adventure.domain.scenario.CharacterCreationBlueprintRevisionConflictException;
import com.dndmaster.adventure.domain.scenario.StorybookProposalEvidenceRequiredException;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleDeletionConflictException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.Map;

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

    @ExceptionHandler(StorybookProposalEvidenceRequiredException.class)
    public ResponseEntity<Map<String, String>> proposalEvidenceRequired(StorybookProposalEvidenceRequiredException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "error", "STORYBOOK_PROPOSAL_EVIDENCE_REQUIRED",
                "message", exception.getMessage()));
    }

    @ExceptionHandler(ScenarioBundleDeletionConflictException.class)
    public ResponseEntity<Map<String, String>> deletionConflict(ScenarioBundleDeletionConflictException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "error", "ACTIVE_ADVENTURE_REFERENCES_BUNDLE",
                "message", "진행 중인 모험이 사용 중인 자료는 삭제할 수 없습니다."));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> stateConflict(IllegalStateException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "error", "ADVENTURE_START_BLOCKED",
                "message", exception.getMessage() == null ? "모험 시작 조건을 충족하지 못했습니다." : exception.getMessage(),
                "recovery", "블루프린트를 재컴파일·발행한 뒤 start/recover로 세션을 복구하고 다시 시작하세요."));
    }

    @ExceptionHandler(CharacterCreationBlueprintRevisionConflictException.class)
    public ResponseEntity<Void> blueprintRevisionConflict(CharacterCreationBlueprintRevisionConflictException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).build();
    }
}
