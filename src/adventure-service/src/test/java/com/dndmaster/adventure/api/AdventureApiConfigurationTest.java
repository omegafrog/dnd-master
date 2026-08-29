package com.dndmaster.adventure.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.adventure.application.combat.AiCombatPort;
import com.dndmaster.adventure.application.combat.CombatActionCommand;
import com.dndmaster.adventure.application.combat.CombatActorRole;
import com.dndmaster.adventure.application.combat.CombatMapPort;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.CharacterSheetId;
import com.dndmaster.adventure.domain.adventure.RuleSetId;
import java.util.UUID;
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
