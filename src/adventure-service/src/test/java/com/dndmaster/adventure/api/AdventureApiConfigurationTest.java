package com.dndmaster.adventure.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.adventure.application.combat.AiCombatPort;
import com.dndmaster.adventure.application.combat.CombatActionCommand;
import com.dndmaster.adventure.application.combat.CombatActorRole;
import com.dndmaster.adventure.application.combat.CombatMapPort;
import com.dndmaster.adventure.application.combat.CombatMapMoveCommand;
import com.dndmaster.adventure.application.combat.CombatMapPreviewCommand;
import com.dndmaster.adventure.application.combat.CombatMapPreviewPosition;
import com.dndmaster.adventure.application.combat.CombatMapMovementPreviewRejectedException;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.CharacterSheetId;
import com.dndmaster.adventure.domain.adventure.RuleSetId;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import org.junit.jupiter.api.Test;

class AdventureApiConfigurationTest {
    private final AiCombatPort aiCombatPort = new AdventureApiConfiguration().aiCombatPort();

    @Test
    void adjudicates_natural_twenty_as_critical_hit() {
        assertEquals("critical hit (natural 20)", aiCombatPort.adjudicate(command(), 20));
    }

    @Test
    void adjudicates_natural_one_as_critical_miss() {
        assertEquals("critical miss (natural 1)", aiCombatPort.adjudicate(command(), 1));
    }

    @Test
    void defers_non_natural_roll_without_attack_bonus_and_target_ac() {
        assertEquals("판정 보류: 대상 AC와 공격 보정이 필요합니다 (d20=12).",
                aiCombatPort.adjudicate(command(), 12));
    }

    @Test
    void wires_non_player_ai_state_control_to_combat_map_gateway() throws Exception {
        AtomicReference<String> requestPath = new AtomicReference<>();
        AtomicReference<String> requestToken = new AtomicReference<>();
        AtomicInteger requestCount = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/internal/v1/combat-maps/", exchange -> {
            requestCount.incrementAndGet();
            requestPath.set(exchange.getRequestURI().getPath());
            requestToken.set(exchange.getRequestHeaders().getFirst("X-Internal-Token"));
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().close();
        });
        server.start();
        try {
            AiCombatPort configured = new AdventureApiConfiguration().aiCombatPort(
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/", "test-token");
            UUID mapId = UUID.randomUUID();
            configured.controlState(new CombatActionCommand(UUID.randomUUID(), AdventureId.generate(),
                    new RuleSetId(UUID.randomUUID()), new CharacterSheetId(UUID.randomUUID()), mapId,
                    CombatActorRole.ENEMY, "attack", "A1>B1", UUID.randomUUID(), UUID.randomUUID(), 0L));

            assertEquals("/internal/v1/combat-maps/" + mapId + "/ai-state", requestPath.get());
            assertEquals("test-token", requestToken.get());
            assertEquals(1, requestCount.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void wires_internal_token_to_player_map_movement_gateway() throws Exception {
        AtomicReference<String> requestPath = new AtomicReference<>();
        AtomicReference<String> requestToken = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> {
            requestPath.set(exchange.getRequestURI().getPath());
            requestToken.set(exchange.getRequestHeaders().getFirst("X-Internal-Token"));
            String body = exchange.getRequestURI().getPath().endsWith("/runtime")
                    ? "{\"edition\":\"DND_5E_2024\",\"version\":1}"
                    : "{}";
            byte[] bytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.getResponseBody().close();
        });
        server.start();
        try {
            CombatMapPort configured = new AdventureApiConfiguration().combatMapPort(
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/", "test-token");
            UUID mapId = UUID.randomUUID();
            configured.validateAndMove(new CombatActionCommand(UUID.randomUUID(), AdventureId.generate(),
                    UUID.randomUUID(), new RuleSetId(UUID.randomUUID()), new CharacterSheetId(UUID.randomUUID()), mapId,
                    CombatActorRole.PLAYER, "MOVE", "0,0;1,0", UUID.randomUUID(), UUID.randomUUID(), 1L));

            assertEquals("/internal/v1/combat-maps/" + mapId + "/moves", requestPath.get());
            assertEquals("test-token", requestToken.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void sends_a_confirmed_preview_to_the_durable_movement_operation_boundary() throws Exception {
        AtomicReference<String> requestPath = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> {
            requestPath.set(exchange.getRequestURI().getPath());
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            byte[] body = "{\"mapVersion\":4,\"status\":\"COMMITTED\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.getResponseBody().close();
        });
        server.start();
        try {
            CombatMapPort configured = new AdventureApiConfiguration().combatMapPort(
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/", "test-token");
            UUID mapId = UUID.randomUUID();
            CombatActionCommand command = new CombatActionCommand(UUID.randomUUID(), AdventureId.generate(),
                    UUID.randomUUID(), new RuleSetId(UUID.randomUUID()), new CharacterSheetId(UUID.randomUUID()), mapId,
                    CombatActorRole.PLAYER, "MOVE", "0,0;1,0", UUID.randomUUID(), UUID.randomUUID(), 3L);

            configured.move(new CombatMapMoveCommand(command, 5, 3L, "DND_5E_2024", "preview-identity", List.of()));

            assertEquals("/internal/v1/combat-maps/" + mapId + "/movement-operations", requestPath.get());
            assertTrue(requestBody.get().contains("\"fingerprint\":\"preview-identity\""));
            assertTrue(!requestBody.get().contains("waypoints"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void wires_player_public_movement_preview_to_combat_map_gateway() throws Exception {
        AtomicReference<String> requestPath = new AtomicReference<>();
        AtomicReference<String> requestToken = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        UUID mapId = UUID.randomUUID();
        server.createContext("/", exchange -> {
            requestPath.set(exchange.getRequestURI().getPath());
            requestToken.set(exchange.getRequestHeaders().getFirst("X-Internal-Token"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            byte[] response = ("{\"orderedPositions\":[{\"x\":1,\"y\":1},{\"x\":2,\"y\":1}],\"distance\":5,\"baseMapVersion\":3,\"fingerprint\":\"fp\"}")
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.getResponseBody().close();
        });
        server.start();
        try {
            CombatMapPort configured = new AdventureApiConfiguration().combatMapPort(
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/", "test-token");
            UUID ownerId = UUID.randomUUID();
            UUID tokenId = UUID.randomUUID();
            var result = configured.preview(new CombatMapPreviewCommand(mapId, ownerId, tokenId,
                    new CombatMapPreviewPosition(2, 1), List.of(new CombatMapPreviewPosition(1, 1)),
                    "DND_5E_2024", 3));

            assertEquals("/internal/v1/combat-maps/" + mapId + "/movement-previews", requestPath.get());
            assertEquals("test-token", requestToken.get());
            assertTrue(requestBody.get().contains(ownerId.toString()));
            assertTrue(requestBody.get().contains("\"waypoints\":[{\"x\":1,\"y\":1}]"));
            assertEquals(5, result.distance());
            assertEquals(3, result.baseMapVersion());
            assertEquals("fp", result.fingerprint());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void preserves_stale_preview_status_for_the_adventure_boundary() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> {
            byte[] response = "{\"code\":\"STALE_MOVEMENT_PROPOSAL\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(409, response.length);
            exchange.getResponseBody().write(response);
            exchange.getResponseBody().close();
        });
        server.start();
        try {
            CombatMapPort configured = new AdventureApiConfiguration().combatMapPort(
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/", "test-token");
            var exception = assertThrows(CombatMapMovementPreviewRejectedException.class, () -> configured.preview(
                    new CombatMapPreviewCommand(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                            new CombatMapPreviewPosition(1, 1), List.of(), "DND_5E_2024", 3)));

            assertEquals(409, exception.status());
            assertEquals("STALE_MOVEMENT_PROPOSAL", exception.code());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void preserves_stale_status_for_final_player_movement() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> {
            byte[] response = "{\"code\":\"STALE_MOVEMENT_PROPOSAL\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(409, response.length);
            exchange.getResponseBody().write(response);
            exchange.getResponseBody().close();
        });
        server.start();
        try {
            CombatMapPort configured = new AdventureApiConfiguration().combatMapPort(
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/", "test-token");
            CombatActionCommand command = new CombatActionCommand(UUID.randomUUID(), AdventureId.generate(),
                    UUID.randomUUID(), new RuleSetId(UUID.randomUUID()), new CharacterSheetId(UUID.randomUUID()),
                    UUID.randomUUID(), CombatActorRole.PLAYER, "MOVE", "0,0;1,0", UUID.randomUUID(), UUID.randomUUID(), 3L);

            var exception = assertThrows(CombatMapMovementPreviewRejectedException.class,
                    () -> configured.move(new com.dndmaster.adventure.application.combat.CombatMapMoveCommand(
                            command, 5, 3, "DND_5E_2024")));

            assertEquals(409, exception.status());
            assertEquals("STALE_MOVEMENT_PROPOSAL", exception.code());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void skips_ai_state_control_for_non_player_actions_without_movement() throws Exception {
        AtomicInteger requestCount = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> {
            requestCount.incrementAndGet();
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().close();
        });
        server.start();
        try {
            AiCombatPort configured = new AdventureApiConfiguration().aiCombatPort(
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/", "test-token");
            configured.controlState(new CombatActionCommand(UUID.randomUUID(), AdventureId.generate(),
                    new RuleSetId(UUID.randomUUID()), new CharacterSheetId(UUID.randomUUID()), UUID.randomUUID(),
                    CombatActorRole.ENEMY, "attack", null, UUID.randomUUID(), UUID.randomUUID(), 0L));
            assertEquals(0, requestCount.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void skips_ai_state_control_for_player_actions() throws Exception {
        AtomicReference<String> requestPath = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> {
            requestPath.set(exchange.getRequestURI().getPath());
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().close();
        });
        server.start();
        try {
            AiCombatPort configured = new AdventureApiConfiguration().aiCombatPort(
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/", "test-token");
            configured.controlState(command());
            assertTrue(requestPath.get() == null);
        } finally {
            server.stop(0);
        }
    }

    private static CombatActionCommand command() {
        return new CombatActionCommand(UUID.randomUUID(), AdventureId.generate(), new RuleSetId(UUID.randomUUID()),
                new CharacterSheetId(UUID.randomUUID()), CombatActorRole.PLAYER, "attack", null);
    }
}
