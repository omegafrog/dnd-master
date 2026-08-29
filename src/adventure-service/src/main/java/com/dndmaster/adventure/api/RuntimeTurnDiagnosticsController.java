package com.dndmaster.adventure.api;

import com.dndmaster.adventure.application.runtime.RuntimeTurnDiagnosticsApplicationService;
import com.dndmaster.adventure.application.runtime.RuntimeTurnDiagnosticsApplicationService.RuntimeTurnDiagnosticsView;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Development-only, authenticated and read-only runtime diagnostics. */
@RestController
@RequestMapping("/api/v1/internal/runtime/diagnostics")
public final class RuntimeTurnDiagnosticsController {
    private final RuntimeTurnDiagnosticsApplicationService service;
    private final ApiRequestGuard requestGuard;
    private final boolean enabled;

    @Autowired
    public RuntimeTurnDiagnosticsController(RuntimeTurnDiagnosticsApplicationService service,
            @Value("${adventure.runtime.diagnostics.enabled:false}") boolean enabled,
            @Value("${adventure.integration.internal-token:${INTERNAL_SERVICE_TOKEN:}}") String internalToken) {
        this(service, enabled, new ApiRequestGuard(internalToken));
    }

    public RuntimeTurnDiagnosticsController(RuntimeTurnDiagnosticsApplicationService service, boolean enabled,
            ApiRequestGuard requestGuard) {
        this.service = service;
        this.enabled = enabled;
        this.requestGuard = requestGuard;
    }

    @GetMapping("/turns/{turnId}")
    RuntimeTurnDiagnosticsView read(@PathVariable UUID turnId,
            @RequestHeader(value = "X-Internal-Token", required = false) String internalToken) {
        requestGuard.internal(internalToken);
        if (!enabled) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "runtime diagnostics unavailable");
        return service.readByTurnId(turnId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "runtime turn not found"));
    }

    @GetMapping("/commands/{commandId}")
    RuntimeTurnDiagnosticsView readByCommand(@PathVariable UUID commandId,
            @RequestHeader(value = "X-Internal-Token", required = false) String internalToken) {
        requestGuard.internal(internalToken);
        if (!enabled) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "runtime diagnostics unavailable");
        return service.readByCommandId(commandId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "runtime turn not found"));
    }
}
