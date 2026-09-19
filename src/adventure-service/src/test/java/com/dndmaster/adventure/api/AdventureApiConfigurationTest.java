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
import com.dndmaster.adventure.application.combat.CombatMapMovementStatus;
import com.dndmaster.adventure.application.combat.SpatialCheckRollCommand;
import com.dndmaster.adventure.application.combat.EnemyObservationRollCommand;
import com.dndmaster.adventure.application.combat.CombatMapSpatialTurnCommand;
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
    void sends_spatial_check_roll_to_the_typed_dice_gateway_with_stable_check_identity() throws Exception {
        AtomicReference<String> requestPath = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        AtomicReference<String> idempotencyKey = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> {
            requestPath.set(exchange.getRequestURI().getPath());
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            idempotencyKey.set(exchange.getRequestHeaders().getFirst("Idempotency-Key"));
            byte[] body = "{\"total\":17}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.getResponseBody().close();
        });
        server.start();
        try {
            CombatMapPort configured = new AdventureApiConfiguration().combatMapPort(
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/", "test-token");
            UUID checkId = UUID.randomUUID();
            UUID commandId = UUID.randomUUID();
            var command = new SpatialCheckRollCommand(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                    new RuleSetId(UUID.randomUUID()), UUID.randomUUID(), checkId, UUID.randomUUID(), commandId,
                    "dnd5e.perception", "2d6", 3, 15, 4L);

            assertEquals(17, configured.rollSpatialCheck(command));
            assertEquals("/internal/v1/dice-rolls/player", requestPath.get());
            assertEquals(commandId.toString(), idempotencyKey.get());
            assertTrue(requestBody.get().contains("\"ruleReference\":\"dnd5e.perception\""));
            assertTrue(requestBody.get().contains("\"count\":2"));
            assertTrue(requestBody.get().contains("\"sides\":6"));
            assertTrue(requestBody.get().contains("\"modifier\":3"));
            assertTrue(requestBody.get().contains("\"commandId\":\"" + commandId + "\""));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void wires_enemy_observation_roll_to_the_dice_roll_service_url() throws Exception {
        AtomicReference<String> requestPath = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> {
            requestPath.set(exchange.getRequestURI().getPath());
            byte[] body = "{\"total\":12}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.getResponseBody().close();
        });
        server.start();
        try {
            var configured = new AdventureApiConfiguration().enemyObservationRollPort(
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/", "test-token");
            var command = new EnemyObservationRollCommand(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                    new RuleSetId(UUID.randomUUID()), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                    "dnd5e.perception", "1d20", 0, 12, 1L);
            assertEquals(12, configured.rollEnemyObservation(command));
            assertEquals("/internal/v1/dice-rolls/enemy-observation", requestPath.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void sends_check_resume_command_identity_to_the_combat_map_gateway() throws Exception {
        AtomicReference<String> requestPath = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        AtomicReference<String> idempotencyKey = new AtomicReference<>();
        UUID mapId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        UUID checkId = UUID.randomUUID();
        UUID commandId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> {
            requestPath.set(exchange.getRequestURI().getPath());
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            idempotencyKey.set(exchange.getRequestHeaders().getFirst("Idempotency-Key"));
            byte[] body = ("{\"operationId\":\"%s\",\"status\":\"COMMITTED\",\"mapVersion\":1,\"requestedPath\":[],\"traversedPath\":[],\"publicEvents\":[]}").formatted(operationId).getBytes();
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.getResponseBody().close();
        });
        server.start();
        try {
            CombatMapPort configured = new AdventureApiConfiguration().combatMapPort(
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/", "test-token");
            configured.resumeMovementOperation(mapId, operationId,
                    new com.dndmaster.adventure.application.combat.CombatMapCheckSubmission(
                            commandId, operationId, checkId, true, ownerId,
                            com.dndmaster.adventure.application.combat.CombatMapCheckActor.PLAYER));

            assertEquals("/internal/v1/combat-maps/" + mapId + "/movement-operations/" + operationId + "/resume", requestPath.get());
            assertEquals(commandId.toString(), idempotencyKey.get());
            assertTrue(requestBody.get().contains("\"commandId\":\"" + commandId + "\""));
            assertTrue(requestBody.get().contains("\"checkId\":\"" + checkId + "\""));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void sends_cancel_command_identity_to_the_combat_map_gateway() throws Exception {
        AtomicReference<String> requestPath = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        AtomicReference<String> idempotencyKey = new AtomicReference<>();
        UUID mapId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        UUID cancelCommandId = UUID.randomUUID();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> {
            requestPath.set(exchange.getRequestURI().getPath());
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            idempotencyKey.set(exchange.getRequestHeaders().getFirst("Idempotency-Key"));
            byte[] body = ("{\"operationId\":\"%s\",\"status\":\"CANCELLED\",\"mapVersion\":1,\"requestedPath\":[],\"traversedPath\":[],\"publicEvents\":[]}").formatted(operationId).getBytes();
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.getResponseBody().close();
        });
        server.start();
        try {
            CombatMapPort configured = new AdventureApiConfiguration().combatMapPort(
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/", "test-token");

            configured.cancelMovementOperation(mapId, operationId, cancelCommandId);

            assertEquals("/internal/v1/combat-maps/" + mapId + "/movement-operations/" + operationId, requestPath.get());
            assertEquals(cancelCommandId.toString(), idempotencyKey.get());
            assertTrue(requestBody.get().contains("\"operationId\":\"" + operationId + "\""));
            assertTrue(requestBody.get().contains("\"commandId\":\"" + cancelCommandId + "\""));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void wires_duration_advance_to_the_map_gateway_with_the_command_id_header() throws Exception {
        AtomicReference<String> requestPath = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        AtomicReference<String> idempotencyKey = new AtomicReference<>();
        UUID mapId = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        UUID commandId = UUID.randomUUID();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> {
            requestPath.set(exchange.getRequestURI().getPath());
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            idempotencyKey.set(exchange.getRequestHeaders().getFirst("Idempotency-Key"));
            byte[] body = ("{\"mapId\":\"%s\",\"mapVersion\":4,\"publicEvents\":[]}").formatted(mapId).getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.getResponseBody().close();
        });
        server.start();
        try {
            CombatMapPort configured = new AdventureApiConfiguration().combatMapPort(
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/", "test-token");
            configured.advanceDurations(new CombatMapSpatialTurnCommand(mapId, owner, 3, commandId));

            assertEquals("/internal/v1/combat-maps/" + mapId + "/spatial/advance-durations", requestPath.get());
            assertEquals(commandId.toString(), idempotencyKey.get());
            assertTrue(requestBody.get().contains("\"commandId\":\"" + commandId + "\""));
            assertTrue(requestBody.get().contains("\"expectedVersion\":3"));
        } finally {
            server.stop(0);
        }
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

            assertEquals("/internal/v1/combat-maps/" + mapId + "/movement-operations", requestPath.get());
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

            configured.move(new CombatMapMoveCommand(command, 5, 3L, "DND_5E_2024", "preview-identity",
                    List.of(new CombatMapPreviewPosition(0, 0))));

            assertEquals("/internal/v1/combat-maps/" + mapId + "/movement-operations", requestPath.get());
            assertTrue(requestBody.get().contains("\"fingerprint\":\"preview-identity\""));
            assertTrue(requestBody.get().contains("\"waypoints\":[{\"x\":0,\"y\":0}]"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void maps_combat_map_storage_state_to_adventure_retry_outcome() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> {
            byte[] body = "{\"mapVersion\":4,\"status\":\"READY_TO_COMMIT\"}"
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8);
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

            var result = configured.move(new CombatMapMoveCommand(command, 5, 3L, "DND_5E_2024"));

            assertEquals(CombatMapMovementStatus.CHECK_REQUIRED, result.status());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void replays_the_typed_interruption_result_with_requested_and_traversed_paths() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> {
            byte[] body = ("{\"mapVersion\":5,\"status\":\"INTERRUPTED\","
                    + "\"requestedPath\":[{\"x\":0,\"y\":0},{\"x\":1,\"y\":0}],"
                    + "\"traversedPath\":[{\"x\":0,\"y\":0}],"
                    + "\"finalPosition\":{\"x\":0,\"y\":0},"
                    + "\"publicEvents\":[\"FEATURE_REVEALED\"],\"interruptionReason\":\"FEATURE_REVEALED\"}")
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8);
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
            CombatMapMoveCommand move = new CombatMapMoveCommand(command, 5, 3L, "DND_5E_2024", "preview", List.of());

            var first = configured.move(move);
            var replay = configured.move(move);

            assertEquals(first, replay);
            assertEquals(CombatMapMovementStatus.INTERRUPTED, first.status());
            assertEquals(List.of(new CombatMapPreviewPosition(0, 0), new CombatMapPreviewPosition(1, 0)), first.requestedPath());
            assertEquals(List.of(new CombatMapPreviewPosition(0, 0)), first.traversedPath());
            assertEquals(new CombatMapPreviewPosition(0, 0), first.finalPosition());
            assertEquals("FEATURE_REVEALED", first.interruptionReason());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void preserves_typed_conflict_from_movement_operation_endpoint() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> {
            byte[] body = "{\"code\":\"MOVEMENT_OPERATION_IN_PROGRESS\"}"
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(409, body.length);
            exchange.getResponseBody().write(body);
            exchange.getResponseBody().close();
        });
        server.start();
        try {
            CombatMapPort configured = new AdventureApiConfiguration().combatMapPort(
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/", "test-token");

            var exception = assertThrows(CombatMapMovementPreviewRejectedException.class,
                    () -> configured.movementOperation(UUID.randomUUID(), UUID.randomUUID()));

            assertEquals(409, exception.status());
            assertEquals("MOVEMENT_OPERATION_IN_PROGRESS", exception.code());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void reconnects_through_the_server_latest_movement_operation_boundary() throws Exception {
        AtomicReference<String> requestPath = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        UUID mapId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        server.createContext("/", exchange -> {
            requestPath.set(exchange.getRequestURI().getPath());
            byte[] body = ("{\"operationId\":\"" + operationId
                    + "\",\"status\":\"RETRY_WAIT\",\"outcomeStatus\":\"RETRY_REQUIRED\",\"mapVersion\":0"
                    + ",\"requestedPath\":[{\"x\":1,\"y\":1},{\"x\":2,\"y\":1}]"
                    + ",\"traversedPath\":[{\"x\":1,\"y\":1}],\"finalPosition\":{\"x\":1,\"y\":1},\"publicEvents\":[]}")
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.getResponseBody().close();
        });
        server.start();
        try {
            CombatMapPort configured = new AdventureApiConfiguration().combatMapPort(
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/", "test-token");

            var result = configured.latestMovementOperation(mapId);

            assertEquals("/internal/v1/combat-maps/" + mapId + "/movement-operations", requestPath.get());
            assertEquals(operationId, result.operationId());
            assertEquals(CombatMapMovementStatus.RETRY_REQUIRED, result.status());
            assertEquals(1, result.traversedPath().size());
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
