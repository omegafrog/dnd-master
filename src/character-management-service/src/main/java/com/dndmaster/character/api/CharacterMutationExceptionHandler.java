package com.dndmaster.character.api;

import com.dndmaster.character.domain.CharacterMutationRejectedException;
import com.dndmaster.character.domain.RuleViolation;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Translates aggregate rule rejections into a stable contract consumable by GM tools. */
@RestControllerAdvice(assignableTypes = CharacterSheetController.class)
public final class CharacterMutationExceptionHandler {
    @ExceptionHandler(CharacterMutationRejectedException.class)
    ResponseEntity<CharacterMutationRejectionResponse> handle(CharacterMutationRejectedException exception) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new CharacterMutationRejectionResponse(
                        "REJECTED",
                        exception.violations(),
                        List.of()));
    }

    public record CharacterMutationRejectionResponse(
            String status,
            List<RuleViolation> violations,
            List<SuggestedCommand> alternatives) {
        public CharacterMutationRejectionResponse {
            violations = List.copyOf(violations);
            alternatives = List.copyOf(alternatives);
        }
    }

    public record SuggestedCommand(String type, String label, java.util.Map<String, String> parameters) {}
}
