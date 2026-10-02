package com.dndmaster.adventure.infrastructure.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndmaster.adventure.evidence.EvidenceCandidate;
import com.dndmaster.adventure.evidence.EvidenceAcquisitionContractException;
import com.dndmaster.adventure.evidence.EvidenceRerankRequest;
import com.dndmaster.adventure.evidence.EvidenceSufficiencyRequest;
import com.dndmaster.adventure.evidence.EvidenceAcquisitionTransientException;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

class HttpEvidenceModelPortsTest {
    private WireMockServer server;
    private final ObjectMapper mapper = new ObjectMapper();
    private final UUID candidateId = UUID.randomUUID();
    private final UUID soloPlayerId = UUID.randomUUID();

    @BeforeEach
    void startServer() {
        server = new WireMockServer(0);
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop();
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void sends_only_the_candidate_contract_to_the_fixed_policy_reranker_with_internal_token(CapturedOutput output) {
        server.stubFor(post(urlEqualTo("/internal/v1/gm/evidence-rerank"))
                .withHeader("X-Internal-Token", equalTo("internal-token"))
                .withRequestBody(equalToJson("""
                        {"soloPlayerId":"%s","query":"question","taskContext":"RULE_GUIDANCE","candidates":[
                          {"evidenceId":"%s","documentType":"RULEBOOK","locator":"p:1","excerpt":"rule text"}
                        ]}
                        """.formatted(soloPlayerId, candidateId)))
                .willReturn(aResponse().withHeader("Content-Type", "application/json")
                        .withBody("{" + "\"orderedCandidateIds\":[\"" + candidateId + "\"]}")));

        var port = new HttpEvidenceRerankerPort(HttpClient.newHttpClient(), URI.create(server.baseUrl() + "/"), Duration.ofSeconds(2), mapper, "internal-token");

        assertEquals(List.of(candidateId), port.rerank(new EvidenceRerankRequest("RULE_GUIDANCE", "question", List.of(candidate()), soloPlayerId)));
        server.verify(postRequestedFor(urlEqualTo("/internal/v1/gm/evidence-rerank")));
        org.assertj.core.api.Assertions.assertThat(output).doesNotContain("evidence reranking contract failure");
    }

    @Test
    void maps_a_transient_model_failure_to_the_common_retry_signal() {
        server.stubFor(post(urlEqualTo("/internal/v1/gm/evidence-sufficiency"))
                .willReturn(aResponse().withStatus(503).withBody("{\"code\":\"EVIDENCE_PROVIDER_UNAVAILABLE\"}")));
        var port = new HttpEvidenceSufficiencyJudgePort(HttpClient.newHttpClient(), URI.create(server.baseUrl() + "/"), Duration.ofSeconds(2), mapper, "internal-token");

        assertThrows(EvidenceAcquisitionTransientException.class,
                () -> port.judge(new EvidenceSufficiencyRequest("RULE_GUIDANCE", "question", List.of(candidate()), List.of(), 0)));
    }

    @Test
    void maps_a_transient_reranker_failure_to_the_common_retry_signal_with_http_status() {
        server.stubFor(post(urlEqualTo("/internal/v1/gm/evidence-rerank"))
                .willReturn(aResponse().withStatus(503)));
        var port = new HttpEvidenceRerankerPort(HttpClient.newHttpClient(), URI.create(server.baseUrl() + "/"),
                Duration.ofSeconds(2), mapper, "internal-token");

        var failure = assertThrows(EvidenceAcquisitionTransientException.class,
                () -> port.rerank(new EvidenceRerankRequest("RULE_GUIDANCE", "question", List.of(candidate()), soloPlayerId)));

        assertEquals("evidence reranking is unavailable (HTTP 503)", failure.getMessage());
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void logs_diagnostic_context_only_for_a_422_reranker_contract_failure(CapturedOutput output) {
        server.stubFor(post(urlEqualTo("/internal/v1/gm/evidence-rerank"))
                .willReturn(aResponse().withStatus(422)
                        .withBody("{\"detail\":\"EVIDENCE_MODEL_OUTPUT_INVALID\"}")));
        var port = new HttpEvidenceRerankerPort(HttpClient.newHttpClient(), URI.create(server.baseUrl() + "/"),
                Duration.ofSeconds(2), mapper, "internal-token");

        assertThrows(EvidenceAcquisitionContractException.class,
                () -> port.rerank(new EvidenceRerankRequest("RULE_GUIDANCE", "question", List.of(candidate()), soloPlayerId)));

        org.assertj.core.api.Assertions.assertThat(output)
                .contains("evidence reranking contract failure status=422")
                .contains("policy=RULE_GUIDANCE")
                .contains("candidateCount=1")
                .contains("responseMarker=EVIDENCE_MODEL_OUTPUT_INVALID");
    }

    @Test
    void validates_judge_response_identifiers_and_reasons_before_returning_it() {
        UUID outside = UUID.randomUUID();
        server.stubFor(post(urlEqualTo("/internal/v1/gm/evidence-sufficiency"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json")
                        .withBody("{" + "\"sufficient\":true,\"selectedEvidenceIds\":[\"" + outside
                                + "\"],\"selectionReasons\":{\"" + outside + "\":\"not supplied\"},\"missing\":\"\"}")));
        var port = new HttpEvidenceSufficiencyJudgePort(HttpClient.newHttpClient(), URI.create(server.baseUrl() + "/"), Duration.ofSeconds(2), mapper, "internal-token");

        assertThrows(EvidenceAcquisitionContractException.class,
                () -> port.judge(new EvidenceSufficiencyRequest("RULE_GUIDANCE", "question", List.of(candidate()), List.of(), 0)));
    }

    private EvidenceCandidate candidate() {
        return new EvidenceCandidate(candidateId, "document", "RULEBOOK", "p:1", "rule text");
    }
}
