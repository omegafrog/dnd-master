package com.dndmaster.adventure;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndmaster.adventure.application.combat.AdventureCombatApplicationService;
import com.dndmaster.adventure.application.combat.CombatActionCommand;
import com.dndmaster.adventure.application.combat.CombatActorRole;
import com.dndmaster.adventure.application.combat.CombatOperation;
import com.dndmaster.adventure.application.combat.CombatOperationRepository;
import com.dndmaster.adventure.application.combat.CrossContextCallException;
import com.dndmaster.adventure.application.combat.RuntimeCombatRejectionException;
import com.dndmaster.adventure.application.combat.CrossContextHttpCombatGateway;
import com.dndmaster.adventure.infrastructure.integration.CrossContextHttpRuleIntentClassificationGateway;
import com.dndmaster.adventure.application.guidance.RuleQueryIntent;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.CharacterSheetId;
import com.dndmaster.adventure.domain.adventure.RuleSetId;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class CrossContextHttpIntegrationTest {
    private WireMockServer server;

    @AfterEach
    void stopServer() { if (server != null) server.stop(); }

    @Test
    void retries_only_failed_bc_step_and_never_duplicates_completed_adjudication() {
        server = new WireMockServer(0);
        server.start();
        server.stubFor(get(urlPathMatching("/internal/v1/character-sheets/.*/runtime"))
                .willReturn(aResponse().withStatus(200).withBody("""
                        {"characterSheetId":"a1b2c3d4-e5f6-4789-abcd-0123456789ab",
                         "edition":"DND_5E_2024","version":7,
                         "currentHitPoints":12,"currency":25,
                         "ownedEquipment":[{"name":"Longsword","quantity":1}],
                         "unknownFutureField":{"source":"character-service"}}
                        """)));
        server.stubFor(post(urlEqualTo("/rolls")).willReturn(aResponse().withStatus(200).withBody("17")));
        server.stubFor(post(urlPathMatching("/internal/v1/combat-maps/.*/moves")).willReturn(aResponse().withStatus(200)));
        server.stubFor(post(urlPathMatching("/internal/v1/combat-maps/.*/ai-state")).willReturn(aResponse().withStatus(200)));
        server.stubFor(post(urlEqualTo("/ai/adjudications"))
                .inScenario("partial failure").whenScenarioStateIs(Scenario.STARTED)
                .willSetStateTo("recovered").willReturn(aResponse().withStatus(503)));
        server.stubFor(post(urlEqualTo("/ai/adjudications"))
                .inScenario("partial failure").whenScenarioStateIs("recovered")
                .willReturn(aResponse().withStatus(200).withBody("critical hit")));

        var repository = new MemoryRepository();
        var gateway = new CrossContextHttpCombatGateway(
                HttpClient.newHttpClient(), URI.create(server.baseUrl() + "/"), Duration.ofSeconds(2));
        var service = new AdventureCombatApplicationService(repository, gateway, gateway, gateway, gateway);
        UUID operationId = UUID.randomUUID();
        UUID combatMapId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID tokenId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        var command = new CombatActionCommand(
                operationId, AdventureId.generate(), sessionId, new RuleSetId(UUID.randomUUID()),
                new CharacterSheetId(UUID.randomUUID()), combatMapId, CombatActorRole.PLAYER, "attack", "A1>B1>C1",
                ownerId, tokenId, 7L);

        assertThrows(CrossContextCallException.class, () -> service.resolveCombatAction(command));
        assertEquals("critical hit", service.resolveCombatAction(command).judgment());
        assertEquals("critical hit", service.resolveCombatAction(command).judgment());

        String key = operationId.toString();
        String aiStateKey = UUID.nameUUIDFromBytes((operationId + "|ai-state").getBytes(StandardCharsets.UTF_8)).toString();
        assertNotEquals(key, aiStateKey);
        server.verify(exactly(1), getRequestedFor(urlEqualTo("/internal/v1/character-sheets/" + command.characterSheetId().value() + "/runtime"))
                .withHeader("Idempotency-Key", equalTo(key))
                .withHeader("X-Internal-Token", equalTo(""))
                .withHeader("X-Session-ID", equalTo(sessionId.toString()))
                .withHeader("X-Owner-Player-ID", equalTo(ownerId.toString())));
        server.verify(exactly(1), postRequestedFor(urlEqualTo("/rolls")).withHeader("Idempotency-Key", equalTo(key)));
        server.verify(exactly(1), postRequestedFor(urlPathMatching("/internal/v1/combat-maps/.*/moves"))
                .withRequestBody(equalToJson("""
                        {"playerId":"%s","tokenId":"%s","positions":[{"x":0,"y":0},{"x":1,"y":0},{"x":2,"y":0}],
                         "distance":10,"appliedEdition":"DND_5E_2024","commandId":"%s","expectedVersion":7}
                        """.formatted(ownerId, tokenId, key))));
        server.verify(exactly(1), postRequestedFor(urlPathMatching("/internal/v1/combat-maps/.*/ai-state"))
                .withRequestBody(equalToJson("""
                        {"ownerId":"%s","tokenId":"%s","x":2,"y":0,"commandId":"%s","expectedVersion":8,"layers":[]}
                        """.formatted(ownerId, tokenId, aiStateKey))));
        server.verify(exactly(2), postRequestedFor(urlEqualTo("/ai/adjudications")).withHeader("Idempotency-Key", equalTo(key)));
    }

    @Test
    void rejects_zero_hit_point_character_before_dice_roll() {
        server = new WireMockServer(0);
        server.start();
        server.stubFor(get(urlPathMatching("/internal/v1/character-sheets/.*/runtime"))
                .willReturn(aResponse().withStatus(200).withBody("""
                        {"edition":"DND_5E_2024","version":7,
                         "characterState":"{\\\"currentHitPoints\\\":0}"}
                        """)));

        var repository = new MemoryRepository();
        var gateway = new CrossContextHttpCombatGateway(
                HttpClient.newHttpClient(), URI.create(server.baseUrl() + "/"), Duration.ofSeconds(2));
        var service = new AdventureCombatApplicationService(repository, gateway, gateway, gateway, gateway);
        var command = new CombatActionCommand(
                UUID.randomUUID(), AdventureId.generate(), UUID.randomUUID(), new RuleSetId(UUID.randomUUID()),
                new CharacterSheetId(UUID.randomUUID()), UUID.randomUUID(), CombatActorRole.PLAYER, "attack", "A1>B1",
                UUID.randomUUID(), UUID.randomUUID(), 7L);

        assertThrows(RuntimeCombatRejectionException.class, () -> service.resolveCombatAction(command));
        server.verify(exactly(0), postRequestedFor(urlEqualTo("/rolls")));
        server.verify(exactly(0), postRequestedFor(urlPathMatching("/internal/v1/combat-maps/.*/moves")));
        server.verify(exactly(0), postRequestedFor(urlEqualTo("/ai/adjudications")));
    }

    @Test
    void classifies_gm_intent_via_internal_ai_gateway() {
        server = new WireMockServer(0);
        server.start();
        server.stubFor(post(urlEqualTo("/internal/v1/gm/intent-classifications"))
                .willReturn(aResponse().withStatus(200).withBody("{\"queryIntent\":\"STORY\"}")));

        var gateway = new CrossContextHttpRuleIntentClassificationGateway(
                HttpClient.newHttpClient(), URI.create(server.baseUrl() + "/"), Duration.ofSeconds(2), new com.fasterxml.jackson.databind.ObjectMapper());

        assertEquals(RuleQueryIntent.STORY, gateway.classify("What happened in the tavern?"));
        server.verify(exactly(1), postRequestedFor(urlEqualTo("/internal/v1/gm/intent-classifications"))
                .withRequestBody(equalToJson("{\"question\":\"What happened in the tavern?\"}")));
    }

    private static final class MemoryRepository implements CombatOperationRepository {
        private final Map<UUID, CombatOperation> values = new HashMap<>();
        @Override public Optional<CombatOperation> findById(UUID operationId) { return Optional.ofNullable(values.get(operationId)); }
        @Override public void save(CombatOperation operation) { values.put(operation.id(), operation); }
    }
}
