package com.dndmaster.aigamemaster.retrieval;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HttpRetrievalAdapterTest {
    private WireMockServer server;
    @BeforeEach void start() { server = new WireMockServer(WireMockConfiguration.options().dynamicPort()); server.start(); }
    @AfterEach void stop() { server.stop(); }

    @Test void rule_adapter_returns_contract_metadata_and_latency() {
        server.stubFor(post(urlEqualTo("/internal/v1/rule-evidence/search")).willReturn(okJson("""
                {"evidence":[{"rulebookId":"00000000-0000-0000-0000-000000000001","locator":"page:1","score":0.91}]}
                """)));
        var c = new RetrievalEvaluationCase("case-1", "rule", "door rule", "00000000-0000-0000-0000-000000000001", "session", "package",
                List.of(new RetrievalReference("00000000-0000-0000-0000-000000000001", "page:1", "v1")), List.of(), List.of(), "rule");
        var result = new HttpRuleRetrievalAdapter(server.baseUrl(), new ObjectMapper()).retrieve(c, 5);
        assertEquals("page:1", result.candidates().getFirst().reference().locator());
        assertEquals("00000000-0000-0000-0000-000000000001", result.candidates().getFirst().ownerId());
        server.verify(postRequestedFor(urlEqualTo("/internal/v1/rule-evidence/search")));
    }

    @Test void story_adapter_returns_versioned_source_locator() {
        server.stubFor(post(urlEqualTo("/internal/v1/story-sources/search")).willReturn(okJson("""
                {"evidence":[{"knowledgeDocumentId":"00000000-0000-0000-0000-000000000002","extractionVersion":3,"locator":"page:2","score":0.8}]}
                """)));
        var c = new RetrievalEvaluationCase("case-2", "scene", "scene clue", "00000000-0000-0000-0000-000000000001", "session", "package",
                List.of(new RetrievalReference("00000000-0000-0000-0000-000000000002", "page:2", "v3")), List.of(), List.of(), "scene");
        var result = new HttpStoryRetrievalAdapter(server.baseUrl(), new ObjectMapper()).retrieve(c, 5);
        assertEquals("v3", result.candidates().getFirst().reference().version());
        assertEquals("page:2", result.candidates().getFirst().reference().locator());
    }
}
