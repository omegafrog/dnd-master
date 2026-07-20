package com.dndmaster.contracts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

class ConsumerProviderContractTest {
    @Test
    void consumer_paths_exist_in_canonical_provider_contracts() throws IOException {
        assertProviderPath("identity-access", "/internal/v1/auth/introspections");
        assertProviderPath("rule-knowledge", "/internal/v1/rule-evidence/search");
        assertProviderPath("character-management", "/internal/v1/character-sheets/{sheetId}");
        assertProviderPath("dice-roll", "/internal/v1/dice-rolls/player");
        assertProviderPath("combat-map", "/internal/v1/combat-maps/{mapId}/moves");
        assertProviderPath("ai-game-master", "/internal/v1/gm/judgments");
        assertProviderPath("adventure", "/internal/v1/adventures/{adventureId}/gm-context");
    }

    @Test
    void consumer_maps_success_client_server_and_timeout_without_leaking_provider_body() {
        assertEquals("ok", consume(() -> new ProviderResponse(200, "ok"), Duration.ofSeconds(1)));
        assertEquals(400, assertThrows(ConsumerFailure.class,
                () -> consume(() -> new ProviderResponse(400, "private validation detail"), Duration.ofSeconds(1))).status);
        assertEquals(502, assertThrows(ConsumerFailure.class,
                () -> consume(() -> new ProviderResponse(500, "provider stack trace"), Duration.ofSeconds(1))).status);
        assertEquals(504, assertThrows(ConsumerFailure.class,
                () -> consume(() -> { throw new TimeoutException("socket detail"); }, Duration.ofMillis(50))).status);
    }

    private static String consume(ProviderCall call, Duration timeout) {
        try {
            ProviderResponse response = call.invoke();
            if (response.status >= 400 && response.status < 500) throw new ConsumerFailure(response.status, "UPSTREAM_REJECTED");
            if (response.status >= 500) throw new ConsumerFailure(502, "UPSTREAM_FAILURE");
            return response.body;
        } catch (TimeoutException exception) {
            throw new ConsumerFailure(504, "UPSTREAM_TIMEOUT");
        }
    }

    @SuppressWarnings("unchecked")
    private static void assertProviderPath(String provider, String path) throws IOException {
        Path root = Path.of(System.getProperty("user.dir")).resolveSibling("contracts");
        Map<String, Object> document = new Yaml().load(Files.readString(root.resolve(provider).resolve("openapi.yaml")));
        assertTrue(((Map<String, Object>) document.get("paths")).containsKey(path));
    }

    private interface ProviderCall { ProviderResponse invoke() throws TimeoutException; }
    private record ProviderResponse(int status, String body) {}
    private static final class ConsumerFailure extends RuntimeException {
        private final int status;
        private ConsumerFailure(int status, String code) { super(code); this.status = status; }
    }
}
