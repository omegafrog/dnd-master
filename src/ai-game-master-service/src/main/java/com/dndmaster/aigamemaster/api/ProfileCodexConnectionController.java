package com.dndmaster.aigamemaster.api;

import com.dndmaster.aigamemaster.application.endpoint.ConnectionContext;
import com.dndmaster.aigamemaster.application.endpoint.ConnectionOperationState;
import com.dndmaster.aigamemaster.application.endpoint.ConnectionOperationType;
import com.dndmaster.aigamemaster.application.endpoint.ProviderConnectionApplicationService;
import com.dndmaster.aigamemaster.application.endpoint.ProviderConnectionState;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/profile/codex-connection")
public final class ProfileCodexConnectionController {
    private final ProviderConnectionApplicationService connections;

    public ProfileCodexConnectionController(ProviderConnectionApplicationService connections) {
        this.connections = connections;
    }

    @GetMapping
    ProviderConnectionState status(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return connections.getStatus(context(authorization));
    }

    @PostMapping("/operations")
    ConnectionOperationState start(@RequestHeader(value = "Authorization", required = false) String authorization,
                                   @RequestBody StartRequest request) {
        try {
            return connections.start(context(authorization), ConnectionOperationType.valueOf(request.type()));
        } catch (IllegalArgumentException failure) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "지원하지 않는 Codex 연결 작업입니다.");
        }
    }

    @GetMapping("/operations/{operationId}")
    ConnectionOperationState operation(@RequestHeader(value = "Authorization", required = false) String authorization,
                                       @PathVariable UUID operationId) {
        return connections.getOperation(context(authorization), operationId);
    }

    @DeleteMapping
    ProviderConnectionState disconnect(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return connections.disconnect(context(authorization));
    }

    private static ConnectionContext context(String authorization) {
        try {
            return new ConnectionContext(authorization);
        } catch (IllegalArgumentException failure) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인한 사용자만 Codex 연결을 관리할 수 있습니다.");
        }
    }

    record StartRequest(String type) { }
}
