package com.dndmaster.adventure.infrastructure.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

import com.dndmaster.adventure.application.runtime.EvidencePack;
import com.dndmaster.adventure.application.runtime.GmContextEnvelope;
import com.dndmaster.adventure.application.runtime.RuntimeEvidence;
import com.dndmaster.adventure.application.runtime.RuntimeEvidenceType;
import com.dndmaster.adventure.domain.adventure.AdventureContext;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
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
                 "combatEnemies":[],"mapEntryRequested":false,"판정제안":{"필요":false},
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
    void maps_model_selected_storybook_and_rulebook_keys_back_to_the_exact_evidence_items() {
        server = new WireMockServer(0);
        server.start();
        UUID storybookId = UUID.randomUUID();
        UUID rulebookId = UUID.randomUUID();
        String storybookKey = "STORYBOOK:" + storybookId + ":2:page=3:chunk=story";
        String rulebookKey = "RULEBOOK:" + rulebookId + ":2:page=63:chunk=rule";
        server.stubFor(post(urlEqualTo("/internal/gm/runtime-turn")).willReturn(aResponse().withStatus(200).withBody("""
                {"scene":"복도","judgment":"조사 판정","narration":"이음새를 살핍니다.","combatStart":false,
                 "combatEnemies":[],"mapEntryRequested":false,"판정제안":{"필요":false},"citedEvidence":["%s","%s"],
                 "situation":{"kind":"CONTINUE","location":"복도","problem":"함정 확인","threat":"없음",
                 "goal":"안전 경로 확인","basis":"FALLBACK","reference":"","required":true}}
                """.formatted(storybookKey, rulebookKey))));
        UUID endpointId = UUID.randomUUID();
        server.stubFor(post(urlEqualTo("/internal/gm/runtime-endpoint")).willReturn(aResponse().withStatus(200)
                .withBody("{\"endpointId\":\"" + endpointId + "\",\"endpointVersion\":\"1970-01-01T00:00:00Z\",\"provider\":\"codex-cli\",\"model\":\"gpt-5.6-luna\",\"reasoning\":\"medium\"}")));
        UUID soloPlayerId = UUID.randomUUID();
        RuntimeEvidence storybook = new RuntimeEvidence(RuntimeEvidenceType.STORYBOOK,
                new KnowledgeDocumentId(storybookId), 2, "page=3:chunk=story", "장면 근거", storybookKey);
        RuntimeEvidence rulebook = new RuntimeEvidence(RuntimeEvidenceType.RULEBOOK,
                new KnowledgeDocumentId(rulebookId), 2, "page=63:chunk=rule", "조사 규칙", rulebookKey);
        GmContextEnvelope context = new GmContextEnvelope(
                AdventureId.generate(), new OwnerPlayerId(soloPlayerId), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), 1, new AdventureContext("복도", "", "살펴본다", ""), null,
                "이음새를 조사한다", new EvidencePack(List.of(storybook), List.of(rulebook), List.of()),
                List.of(), List.of(), "", "codex-cli", "gpt-5.6-luna", "medium",
                new RequestedGmProviderSelection(endpointId, "codex-cli", "gpt-5.6-luna", "medium"), null);
        HttpTypedRuntimeGmAgentPort port = new HttpTypedRuntimeGmAgentPort(
                HttpClient.newHttpClient(), URI.create(server.baseUrl() + "/"), Duration.ofSeconds(2),
                new ObjectMapper(), "service-token", "codex-cli/gpt-5.6-luna=272000");

        var plan = port.plan(context).plan();

        org.junit.jupiter.api.Assertions.assertEquals(List.of(storybook, rulebook), plan.citedEvidence());
    }

    @Test
    void maps_a_structured_check_proposal_and_its_rule_evidence() {
        server = new WireMockServer(0);
        server.start();
        UUID ownerId = UUID.randomUUID();
        UUID characterId = UUID.randomUUID();
        UUID rulebookId = UUID.randomUUID();
        UUID endpointId = UUID.randomUUID();
        String key = "RULEBOOK:" + rulebookId + ":3:page=18";
        server.stubFor(post(urlEqualTo("/internal/gm/runtime-turn")).willReturn(aResponse().withStatus(200).withBody("""
                {"scene":"복도","judgment":"지각 판정","narration":"굴림 결과를 기다립니다.","combatStart":false,
                 "combatEnemies":[],"mapEntryRequested":false,"citedEvidence":["%s"],
                 "판정제안":{"필요":true,"이유":"숨은 움직임을 확인합니다.","판정능력또는기술":"지각",
                 "대상캐릭터ID":"%s","굴림주체":"플레이어","굴림식":"1d20","보정치":2,"난이도":12,
                 "근거키":["%s"],"성공시결과":"움직임의 방향을 파악합니다.","실패시결과":"확신할 단서를 찾지 못합니다."},
                 "situation":{"kind":"CONTINUE","location":"복도","problem":"소리를 확인한다","threat":"불명","goal":"확인","basis":"FALLBACK","reference":"","required":true}}
                """.formatted(key, characterId, key))));
        server.stubFor(post(urlEqualTo("/internal/gm/runtime-endpoint")).willReturn(aResponse().withStatus(200)
                .withBody("{\"endpointId\":\"" + endpointId + "\",\"endpointVersion\":\"1970-01-01T00:00:00Z\",\"provider\":\"codex-cli\",\"model\":\"gpt-5.6-luna\",\"reasoning\":\"medium\"}")));
        RuntimeEvidence rule = new RuntimeEvidence(RuntimeEvidenceType.RULEBOOK,
                new KnowledgeDocumentId(rulebookId), 3, "page=18", "지각 판정 규칙", key);
        GmContextEnvelope context = new GmContextEnvelope(AdventureId.generate(), new OwnerPlayerId(ownerId),
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1,
                new AdventureContext("복도", "", "살펴본다", ""), null, "소리를 확인한다",
                new EvidencePack(List.of(), List.of(rule), List.of()), List.of(), List.of(), "",
                "codex-cli", "gpt-5.6-luna", "medium",
                new RequestedGmProviderSelection(endpointId, "codex-cli", "gpt-5.6-luna", "medium"), null);
        HttpTypedRuntimeGmAgentPort port = new HttpTypedRuntimeGmAgentPort(HttpClient.newHttpClient(),
                URI.create(server.baseUrl() + "/"), Duration.ofSeconds(2), new ObjectMapper(), "service-token",
                "codex-cli/gpt-5.6-luna=272000");

        var plan = port.plan(context).plan();

        org.junit.jupiter.api.Assertions.assertTrue(plan.checkProposal().required());
        org.junit.jupiter.api.Assertions.assertEquals(characterId, plan.checkProposal().characterSheetId());
        org.junit.jupiter.api.Assertions.assertEquals(List.of(key), plan.checkProposal().evidenceKeys());
        org.junit.jupiter.api.Assertions.assertEquals(List.of(rule), plan.citedEvidence());
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
