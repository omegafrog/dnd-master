package com.dndmaster.aigamemaster.api;

import com.dndmaster.aigamemaster.application.endpoint.AgentEndpoint;
import com.dndmaster.aigamemaster.application.endpoint.AgentEndpointRegistry;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/backoffice/agent-endpoints")
public final class AgentEndpointBackofficeController {
    private final AgentEndpointRegistry registry; private final Set<String> adminIds;
    public AgentEndpointBackofficeController(AgentEndpointRegistry registry, @Value("${ai-game-master.backoffice.admin-player-ids:}") String ids) {
        this.registry = registry; this.adminIds = ids.isBlank() ? Set.of() : Set.of(ids.split(","));
    }
    @GetMapping List<EndpointView> list(@RequestHeader("Authorization") String authorization) { requireAdmin(authorization); return registry.list().stream().map(EndpointView::from).toList(); }
    @PutMapping("/{endpointId}") EndpointView save(@RequestHeader("Authorization") String authorization, @PathVariable UUID endpointId, @RequestBody EndpointRequest request) {
        requireAdmin(authorization);
        AgentEndpoint endpoint = new AgentEndpoint(endpointId, request.name(), request.provider(), URI.create(request.baseUrl()), request.model(), request.secretEnvironmentVariable(), request.active(), Instant.now());
        try { registry.save(endpoint); return EndpointView.from(endpoint); } catch (IllegalArgumentException error) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, error.getMessage(), error); }
    }
    @PostMapping("/{endpointId}/health") HealthView health(@RequestHeader("Authorization") String authorization, @PathVariable UUID endpointId) {
        requireAdmin(authorization);
        AgentEndpoint endpoint = registry.list().stream().filter(value -> value.id().equals(endpointId)).findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        try {
            String path = endpoint.provider() == AgentEndpoint.Provider.OLLAMA ? "/api/tags" : "/v1/models";
            HttpRequest.Builder request = HttpRequest.newBuilder(endpoint.baseUrl().resolve(path)).timeout(Duration.ofSeconds(5)).GET();
            if (endpoint.provider() == AgentEndpoint.Provider.OPENAI_COMPATIBLE) {
                String secret = System.getenv(endpoint.secretEnvironmentVariable());
                if (secret == null || secret.isBlank()) return new HealthView(false, null, "configured secret environment variable is unavailable");
                request.header("Authorization", "Bearer " + secret);
            }
            int status = HttpClient.newHttpClient().send(request.build(), java.net.http.HttpResponse.BodyHandlers.discarding()).statusCode();
            return new HealthView(status >= 200 && status < 300, status, status >= 200 && status < 300 ? null : "provider returned HTTP " + status);
        } catch (Exception error) { return new HealthView(false, null, error.getClass().getSimpleName()); }
    }
    private void requireAdmin(String authorization) { String id = authorization != null && authorization.startsWith("Bearer ") ? authorization.substring(7) : ""; if (!adminIds.contains(id)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "ADMIN role is required"); }
    record EndpointRequest(String name, AgentEndpoint.Provider provider, String baseUrl, String model, String secretEnvironmentVariable, boolean active) {}
    record EndpointView(UUID id, String name, AgentEndpoint.Provider provider, String baseUrl, String model, String secretEnvironmentVariable, boolean active, Instant updatedAt) { static EndpointView from(AgentEndpoint value) { return new EndpointView(value.id(), value.name(), value.provider(), value.baseUrl().toString(), value.model(), value.secretEnvironmentVariable(), value.active(), value.updatedAt()); } }
    record HealthView(boolean healthy, Integer statusCode, String detail) {}
}
