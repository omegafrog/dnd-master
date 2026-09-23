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
                new ObjectMapper(), "service-token");

        port.plan(context);

        server.verify(postRequestedFor(urlEqualTo("/internal/gm/runtime-turn"))
                .withRequestBody(equalToJson("""
                        {"soloPlayerId":"%s","endpointId":"%s","provider":"codex-cli",
                         "model":"gpt-5.6-luna","reasoning":"medium"}
                        """.formatted(soloPlayerId, endpointId), true, true)));
    }
}
