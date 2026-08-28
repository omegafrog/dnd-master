package com.dndmaster.adventure;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import com.dndmaster.adventure.domain.scenario.*;
import com.dndmaster.adventure.infrastructure.integration.CrossContextHttpTacticalMapPreparationGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class CrossContextHttpTacticalMapPreparationGatewayTest {
    private WireMockServer server;

    @AfterEach void stop() { if (server != null) server.stop(); }

    @Test void retriesWithPinnedSourceLocatorWhenCatalogDisplayLocatorIsUnavailable() {
        server = new WireMockServer(0); server.start();
        server.stubFor(get(urlPathEqualTo("/internal/v1/story-sources/" + DOC + "/assets"))
                .withQueryParam("locator", equalTo("page 2 image 1"))
                .willReturn(aResponse().withStatus(404)));
        server.stubFor(get(urlPathEqualTo("/internal/v1/story-sources/" + DOC + "/assets"))
                .withQueryParam("locator", equalTo("asset:map-2"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "image/png").withBody(new byte[] {1})));
        server.stubFor(post(urlEqualTo("/internal/v1/combat-maps/prepare"))
                .willReturn(aResponse().withStatus(200).withBody("{\"mapId\":\"" + MAP + "\"}")));

        var gateway = new CrossContextHttpTacticalMapPreparationGateway(HttpClient.newHttpClient(),
                URI.create(server.baseUrl() + "/"), URI.create(server.baseUrl() + "/"), Duration.ofSeconds(2), new ObjectMapper(), "secret");
        assertEquals(MAP, gateway.prepare(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), definition()));
        server.verify(2, getRequestedFor(urlPathEqualTo("/internal/v1/story-sources/" + DOC + "/assets")));
    }

    private static final UUID DOC = UUID.randomUUID();
    private static final UUID MAP = UUID.randomUUID();
    private static MapDefinition definition() {
        return new MapDefinition(MAP, "map", "page 2 image 1", new MapDefinition.MapGrid(0, 0, 1, 0, "5ft"),
                List.of(), List.of(), List.of(), new MapSourceReference(new KnowledgeDocumentId(DOC), 1, "asset:map-2"), .9, MapSafetyStatus.SAFE);
    }
}
