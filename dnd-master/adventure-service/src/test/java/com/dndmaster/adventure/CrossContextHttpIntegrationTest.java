package com.dndmaster.adventure;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndmaster.adventure.application.combat.AdventureCombatApplicationService;
import com.dndmaster.adventure.application.combat.CombatActionCommand;
import com.dndmaster.adventure.application.combat.CombatActorRole;
import com.dndmaster.adventure.application.combat.CombatOperation;
import com.dndmaster.adventure.application.combat.CombatOperationRepository;
import com.dndmaster.adventure.application.combat.CrossContextCallException;
import com.dndmaster.adventure.application.combat.CrossContextHttpCombatGateway;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.CharacterSheetId;
import com.dndmaster.adventure.domain.adventure.RuleSetId;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import java.net.URI;
import java.net.http.HttpClient;
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
        server.stubFor(get(urlPathMatching("/characters/.*")).willReturn(aResponse().withStatus(200)));
        server.stubFor(post(urlEqualTo("/rolls")).willReturn(aResponse().withStatus(200).withBody("17")));
        server.stubFor(post(urlEqualTo("/moves")).willReturn(aResponse().withStatus(200)));
        server.stubFor(post(urlEqualTo("/ai/states")).willReturn(aResponse().withStatus(200)));
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
        var command = new CombatActionCommand(
                operationId, AdventureId.generate(), new RuleSetId(UUID.randomUUID()),
                new CharacterSheetId(UUID.randomUUID()), CombatActorRole.PLAYER, "attack", "A1>B1");

        assertThrows(CrossContextCallException.class, () -> service.resolveCombatAction(command));
        assertEquals("critical hit", service.resolveCombatAction(command).judgment());
        assertEquals("critical hit", service.resolveCombatAction(command).judgment());

        String key = operationId.toString();
        server.verify(exactly(1), getRequestedFor(urlPathMatching("/characters/.*")).withHeader("Idempotency-Key", equalTo(key)));
        server.verify(exactly(1), postRequestedFor(urlEqualTo("/rolls")).withHeader("Idempotency-Key", equalTo(key)));
        server.verify(exactly(1), postRequestedFor(urlEqualTo("/moves")));
        server.verify(exactly(1), postRequestedFor(urlEqualTo("/ai/states")));
        server.verify(exactly(2), postRequestedFor(urlEqualTo("/ai/adjudications")).withHeader("Idempotency-Key", equalTo(key)));
    }

    private static final class MemoryRepository implements CombatOperationRepository {
        private final Map<UUID, CombatOperation> values = new HashMap<>();
        @Override public Optional<CombatOperation> findById(UUID operationId) { return Optional.ofNullable(values.get(operationId)); }
        @Override public void save(CombatOperation operation) { values.put(operation.id(), operation); }
    }
}
