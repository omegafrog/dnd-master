package com.dndmaster.adventure.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.adventure.application.combat.AiCombatDecisionPortAdapter;
import com.dndmaster.adventure.application.combat.AiCombatTurnContext;
import com.dndmaster.adventure.application.combat.AiTacticalInstructionContext;
import com.dndmaster.adventure.application.combat.EnemyCharacterSheet;
import com.dndmaster.adventure.application.combat.EnemyCharacterSheetIdentity;
import com.dndmaster.adventure.domain.combat.CombatEnemyStatBlock;
import com.dndmaster.adventure.domain.combat.CombatParticipant;
import com.dndmaster.adventure.domain.combat.CombatStartPolicy;
import com.dndmaster.adventure.domain.combat.CombatStatBlockSource;
import com.dndmaster.adventure.domain.runtime.CurrentSituation;
import com.dndmaster.adventure.application.runtime.CombatEnemyAbilityProposal;
import com.dndmaster.adventure.infrastructure.integration.HttpTypedCombatDecisionPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HttpTypedCombatDecisionPortTest {
    @Test
    void sends_current_situation_and_source_backed_sheet_to_structured_combat_decision_contract() throws Exception {
        UUID actorId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        UUID endpointId = UUID.randomUUID();
        var server = HttpServer.create(new InetSocketAddress(0), 0);
        var requestBody = new java.util.concurrent.atomic.AtomicReference<String>();
        server.createContext("/internal/gm/runtime-endpoint", exchange -> respond(exchange,
                "{\"endpointId\":\"" + endpointId + "\",\"endpointVersion\":\"" + Instant.EPOCH
                        + "\",\"provider\":\"openai\",\"model\":\"test-model\",\"reasoning\":\"medium\"}"));
        server.createContext("/internal/gm/combat-turn-decision", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            respond(exchange, "{\"actorId\":\"" + actorId + "\",\"kind\":\"ACTION\",\"action\":\"Scimitar\","
                    + "\"targetId\":\"" + targetId + "\",\"citationKeys\":[\"goblin-scimitar\"]}");
        });
        server.start();
        try {
            var identity = new EnemyCharacterSheetIdentity(UUID.randomUUID(), UUID.randomUUID(), 1,
                    UUID.randomUUID(), List.of(UUID.randomUUID()), "goblin");
            var abilities = List.of("STR", "DEX", "CON", "INT", "WIS", "CHA").stream()
                    .map(name -> new CombatEnemyAbilityProposal(name, 10, List.of("goblin-stats"))).toList();
            var sheet = new EnemyCharacterSheet(identity, "Goblin",
                    new CombatEnemyStatBlock(12, 7, 2, "1d6", new CombatStatBlockSource(UUID.randomUUID(), 1, "goblin-stats")),
                    abilities, List.of(new EnemyCharacterSheet.EnemyCombatAction("Scimitar", "Melee attack", List.of("goblin-scimitar"))));
            var encounter = CombatStartPolicy.startFromCommittedGmTurn(true, UUID.randomUUID(), List.of(
                    new CombatParticipant(actorId, "Goblin", CombatParticipant.Controller.AI, 20, null,
                            com.dndmaster.adventure.domain.combat.TurnResources.initial(), sheet.statBlock(), 7, "goblin"),
                    new CombatParticipant(targetId, "Hero", CombatParticipant.Controller.PLAYER, 10, null)));
            var situation = CurrentSituation.initial("Protect the village");
            var context = new AiCombatTurnContext(encounter, encounter.participants().getFirst(),
                    new AiTacticalInstructionContext("Protect the village"), situation.toString(), null,
                    sheet, UUID.randomUUID(), new com.dndmaster.adventure.application.runtime.GmProviderSelection(
                            endpointId, "openai", "test-model", "medium"));
            var port = new HttpTypedCombatDecisionPort(HttpClient.newHttpClient(),
                    java.net.URI.create("http://localhost:" + server.getAddress().getPort() + "/"), Duration.ofSeconds(2),
                    new ObjectMapper(), "test-token", new AiCombatDecisionPortAdapter(ignored -> null));

            var plan = port.planTurn(context);

            assertEquals(actorId, plan.actorId());
            assertEquals("Scimitar", plan.intent().action());
            assertEquals(targetId, plan.targetId());
            assertEquals(List.of("goblin-scimitar"), plan.citationKeys());
            assertTrue(requestBody.get().contains("Protect the village"));
            assertTrue(requestBody.get().contains("goblin-scimitar"));
            assertTrue(requestBody.get().contains("citationKeys is REQUIRED for both ACTION and END_TURN"));
            assertTrue(requestBody.get().contains("MUST be a non-empty JSON array of exact citation keys"));
        } finally {
            server.stop(0);
        }
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, String response) throws java.io.IOException {
        byte[] bytes = response.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (var output = exchange.getResponseBody()) { output.write(bytes); }
    }
}
