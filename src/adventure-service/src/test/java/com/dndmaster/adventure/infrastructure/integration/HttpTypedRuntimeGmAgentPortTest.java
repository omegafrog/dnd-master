package com.dndmaster.adventure.infrastructure.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

import com.dndmaster.adventure.application.runtime.EvidencePack;
import com.dndmaster.adventure.application.runtime.GmContextEnvelope;
import com.dndmaster.adventure.domain.adventure.AdventureContext;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import com.dndmaster.adventure.domain.runtime.RequestedGmProviderSelection;
import com.dndmaster.adventure.domain.runtime.narrative.NarrativeContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class HttpTypedRuntimeGmAgentPortTest {
    private WireMockServer server;

    @AfterEach
    void stopServer() {
        if (server != null) server.stop();
    }

    @Test
    void sends_the_server_confirmed_solo_player_id_to_the_ai_game_master_contract() {
        server = new WireMockServer(0);
        server.start();
        server.stubFor(post(urlEqualTo("/internal/gm/runtime-turn")).willReturn(aResponse().withStatus(200).withBody("""
                {"scene":"scene","judgment":"safe","narration":"quiet","combatStart":false,
                 "combatEnemies":[],"mapEntryRequested":false,
                 "situation":{"kind":"CONTINUE","location":"cellar","problem":"noise","threat":"none",
                 "goal":"inspect","basis":"FALLBACK","reference":"","required":true}}
                """)));
        UUID soloPlayerId = UUID.randomUUID();
        UUID endpointId = UUID.randomUUID();
        server.stubFor(post(urlEqualTo("/internal/gm/runtime-endpoint")).willReturn(aResponse().withStatus(200).withBody("{\"endpointId\":\"" + endpointId + "\",\"endpointVersion\":\"1970-01-01T00:00:00Z\",\"provider\":\"codex-cli\",\"model\":\"gpt-5.6-luna\",\"reasoning\":\"medium\"}")));
        GmContextEnvelope context = new GmContextEnvelope(
                AdventureId.generate(), new OwnerPlayerId(soloPlayerId), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), 1, new AdventureContext("cellar", "quiet", "inspect", "safe"), null,
                "look around", new EvidencePack(List.of(), List.of(), List.of()), List.of(), List.of(), "",
                "codex-cli", "gpt-5.6-luna", "medium",
                new RequestedGmProviderSelection(endpointId, "codex-cli", "gpt-5.6-luna", "medium"),
                new NarrativeContext(soloPlayerId.toString(), "cellar", 0, java.util.Set.of(), List.of(),
                        java.util.Map.of(), List.of(), List.of(), List.of()));
        HttpTypedRuntimeGmAgentPort port = new HttpTypedRuntimeGmAgentPort(
                HttpClient.newHttpClient(), URI.create(server.baseUrl() + "/"), Duration.ofSeconds(2),
                new ObjectMapper(), "service-token", "codex-cli/gpt-5.6-luna=272000");

        port.plan(context);

        server.verify(postRequestedFor(urlEqualTo("/internal/gm/runtime-turn"))
                .withRequestBody(com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath("$.soloPlayerId",
                        com.github.tomakehurst.wiremock.client.WireMock.equalTo(soloPlayerId.toString())))
                .withRequestBody(com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath("$.effectiveEndpointId",
                        com.github.tomakehurst.wiremock.client.WireMock.equalTo(endpointId.toString())))
                .withRequestBody(com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath("$.prompt",
                        com.github.tomakehurst.wiremock.client.WireMock.containing("고정 지침·잠긴 자료"))));
    }
    @Test
    void required_input_overflow_stays_retryable_and_never_calls_runtime_ai() {
        server = new WireMockServer(0);
        server.start();
        UUID endpointId = UUID.randomUUID();
        server.stubFor(post(urlEqualTo("/internal/gm/runtime-endpoint")).willReturn(aResponse().withStatus(200)
                .withBody("{\"endpointId\":\"" + endpointId + "\",\"endpointVersion\":\"1970-01-01T00:00:00Z\","
                        + "\"provider\":\"codex-cli\",\"model\":\"gpt-5.6-luna\",\"reasoning\":\"medium\"}")));
        UUID owner = UUID.randomUUID();
        GmContextEnvelope context = new GmContextEnvelope(AdventureId.generate(), new OwnerPlayerId(owner),
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1,
                new AdventureContext("cellar", "quiet", "inspect", "safe"), null, "look around",
                new EvidencePack(List.of(), List.of(), List.of()), List.of(), List.of("x".repeat(2000)), "",
                "codex-cli", "gpt-5.6-luna", "medium",
                new RequestedGmProviderSelection(endpointId, "codex-cli", "gpt-5.6-luna", "medium"), null);
        var port = new HttpTypedRuntimeGmAgentPort(HttpClient.newHttpClient(), URI.create(server.baseUrl() + "/"),
                Duration.ofSeconds(2), new ObjectMapper(), "service-token", "codex-cli/gpt-5.6-luna=1000");

        org.junit.jupiter.api.Assertions.assertThrows(
                com.dndmaster.adventure.application.runtime.RuntimeGmInputLimitException.class,
                () -> port.plan(context));
        server.verify(0, postRequestedFor(urlEqualTo("/internal/gm/runtime-turn")));
    }

}
