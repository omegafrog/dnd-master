package com.dndmaster.ruleknowledge.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dndmaster.ruleknowledge.application.pipeline.RulebookPipelineApplicationService;
import com.dndmaster.ruleknowledge.application.publication.SourceProvenance;
import com.dndmaster.ruleknowledge.application.registration.RulebookRegistrationRepository;
import com.dndmaster.ruleknowledge.application.registration.StoredRulebookRegistration;
import com.dndmaster.ruleknowledge.application.search.EvidenceCandidate;
import com.dndmaster.ruleknowledge.application.search.EvidenceSearchResult;
import com.dndmaster.ruleknowledge.application.search.HybridEvidenceSearchService;
import com.dndmaster.ruleknowledge.application.search.RuleEvidenceSearchApplicationService;
import com.dndmaster.ruleknowledge.domain.index.ChunkId;
import com.dndmaster.ruleknowledge.domain.rulebook.DocumentType;
import com.dndmaster.ruleknowledge.domain.rulebook.KnowledgeDocumentId;
import com.dndmaster.ruleknowledge.domain.rulebook.OwnerPlayerId;
import com.dndmaster.ruleknowledge.domain.rulebook.ProcessingStatus;
import com.dndmaster.ruleknowledge.domain.rulebook.RulebookFormat;
import com.dndmaster.ruleknowledge.domain.rulebook.RulebookId;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class UnifiedEvidenceCandidateSearchControllerTest {
    private static final UUID OWNER = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Test
    void authenticatesValidatesScopeAndReturnsCandidateProvenance() throws Exception {
        UUID documentId = UUID.randomUUID();
        HybridEvidenceSearchService hybrid = mock(HybridEvidenceSearchService.class);
        when(hybrid.search(any())).thenReturn(new EvidenceSearchResult(List.of(new EvidenceCandidate(
                new KnowledgeDocumentId(documentId), new ChunkId(UUID.randomUUID()), 1, DocumentType.STORYBOOK,
                "page:2", "A hidden passage", new SourceProvenance(2, List.of("Chapter 1"), List.of(), null, "page:2"),
                1, 2, 0.032))));
        MockMvc mockMvc = controller(registration(documentId, ProcessingStatus.INDEXED), hybrid);

        mockMvc.perform(post("/internal/v1/evidence-candidates/search")
                        .header("Authorization", "Bearer " + OWNER).contentType(MediaType.APPLICATION_JSON)
                        .content(request(documentId, 5, 7)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownerId").value(OWNER.toString()))
                .andExpect(jsonPath("$.candidates[0].documentId").value(documentId.toString()))
                .andExpect(jsonPath("$.candidates[0].locator").value("page:2"))
                .andExpect(jsonPath("$.candidates[0].provenance.pageNumber").value(2))
                .andExpect(jsonPath("$.candidates[0].bm25Rank").value(2));
        verify(hybrid).search(any());
    }

    @Test
    void rejectsInvalidLimitsWithoutCallingSearch() throws Exception {
        UUID documentId = UUID.randomUUID();
        HybridEvidenceSearchService hybrid = mock(HybridEvidenceSearchService.class);

        controller(registration(documentId, ProcessingStatus.INDEXED), hybrid).perform(post("/internal/v1/evidence-candidates/search")
                        .header("Authorization", "Bearer " + OWNER).contentType(MediaType.APPLICATION_JSON)
                        .content(request(documentId, 0, 7)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EVIDENCE_SEARCH_INVALID_REQUEST"));
        org.mockito.Mockito.verifyNoInteractions(hybrid);
    }

    @Test
    void rejectsMissingAuthenticationWithStableCode() throws Exception {
        UUID documentId = UUID.randomUUID();
        HybridEvidenceSearchService hybrid = mock(HybridEvidenceSearchService.class);

        controller(registration(documentId, ProcessingStatus.INDEXED), hybrid)
                .perform(post("/internal/v1/evidence-candidates/search").contentType(MediaType.APPLICATION_JSON)
                        .content(request(documentId, 5, 7)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("EVIDENCE_SEARCH_UNAUTHENTICATED"));
        org.mockito.Mockito.verifyNoInteractions(hybrid);
    }

    @Test
    void rejectsForeignDocumentScopeWithStableCode() throws Exception {
        UUID documentId = UUID.randomUUID();
        HybridEvidenceSearchService hybrid = mock(HybridEvidenceSearchService.class);

        controller(registration(documentId, ProcessingStatus.INDEXED, UUID.randomUUID()), hybrid)
                .perform(post("/internal/v1/evidence-candidates/search").header("Authorization", "Bearer " + OWNER)
                        .contentType(MediaType.APPLICATION_JSON).content(request(documentId, 5, 7)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EVIDENCE_SEARCH_SCOPE_FORBIDDEN"));
    }

    @Test
    void returnsStableUnavailableErrorWhenBothSearchAttemptsFail() throws Exception {
        UUID documentId = UUID.randomUUID();
        HybridEvidenceSearchService hybrid = mock(HybridEvidenceSearchService.class);
        when(hybrid.search(any())).thenThrow(new com.dndmaster.ruleknowledge.application.search.EvidenceSearchUnavailableException(new IllegalStateException("down")));

        controller(registration(documentId, ProcessingStatus.INDEXED), hybrid)
                .perform(post("/internal/v1/evidence-candidates/search").header("Authorization", "Bearer " + OWNER)
                        .contentType(MediaType.APPLICATION_JSON).content(request(documentId, 5, 7)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("EVIDENCE_SEARCH_UNAVAILABLE"));
    }

    private static MockMvc controller(StoredRulebookRegistration registration, HybridEvidenceSearchService hybrid) {
        RulebookRegistrationRepository registrations = mock(RulebookRegistrationRepository.class);
        when(registrations.findById(any())).thenReturn(java.util.Optional.of(registration));
        RuleKnowledgeController controller = new RuleKnowledgeController(mock(RulebookPipelineApplicationService.class), registrations,
                mock(RuleEvidenceSearchApplicationService.class), null, null, null, new com.fasterxml.jackson.databind.ObjectMapper(),
                null, "", null, null, hybrid);
        return MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(new com.fasterxml.jackson.databind.ObjectMapper())).build();
    }

    private static String request(UUID documentId, int denseLimit, int bm25Limit) {
        return """
                {"ownerId":"%s","sessionId":"%s","scenarioPackageId":"%s","stageKey":"opening","actionIntent":"STORY","scope":[{"documentId":"%s","extractionVersion":1,"documentType":"STORYBOOK"}],"activeLocators":["page:1"],"query":"where is the passage?","denseLimit":%d,"bm25Limit":%d}
                """.formatted(OWNER, UUID.randomUUID(), UUID.randomUUID(), documentId, denseLimit, bm25Limit);
    }

    private static StoredRulebookRegistration registration(UUID id, ProcessingStatus status) { return registration(id, status, OWNER); }
    private static StoredRulebookRegistration registration(UUID id, ProcessingStatus status, UUID owner) {
        Instant now = Instant.now();
        return new StoredRulebookRegistration(new RulebookId(id), new OwnerPlayerId(owner), "op", "hash", RulebookFormat.TXT,
                1, "storage", status, com.dndmaster.ruleknowledge.domain.rulebook.ExtractionStatus.SUCCESS, "content", List.of(),
                null, 1, now, now, DocumentType.STORYBOOK, "story.txt");
    }
}
