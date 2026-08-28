package com.dndmaster.ruleknowledge.api;

import com.dndmaster.ruleknowledge.application.reset.DevelopmentRagResetService;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/internal/v1/rag")
public final class RagDevelopmentResetController {
    private final DevelopmentRagResetService resetService;
    private final String internalToken;

    public RagDevelopmentResetController(DevelopmentRagResetService resetService, String internalToken) {
        this.resetService = Objects.requireNonNull(resetService, "reset service must not be null");
        this.internalToken = internalToken == null ? "" : internalToken;
    }

    @PostMapping("/reset")
    ResponseEntity<ResetResponse> reset(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestBody ResetRequest request) {
        if (internalToken.isBlank() || !internalToken.equals(token)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "invalid internal token");
        }
        try {
            DevelopmentRagResetService.ResetResult result = resetService.reset(request == null ? null : request.confirmation());
            return ResponseEntity.ok(new ResetResponse("RESET_COMPLETED", result.deletedRows(), result.tables()));
        } catch (DevelopmentRagResetService.ResetException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.code(), exception);
        }
    }

    public record ResetRequest(String confirmation) {
    }

    public record ResetResponse(String status, int deletedRows, java.util.List<String> tables) {
    }
}
