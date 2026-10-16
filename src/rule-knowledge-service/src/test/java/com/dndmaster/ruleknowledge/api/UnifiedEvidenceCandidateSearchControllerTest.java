package com.dndmaster.ruleknowledge.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dndmaster.ruleknowledge.application.pipeline.RulebookPipelineApplicationService;
import com.dndmaster.ruleknowledge.application.catalog.CatalogRulebookRepository;
import com.dndmaster.ruleknowledge.application.catalog.CatalogRulebookRevision;
import com.dndmaster.ruleknowledge.application.publication.SourceProvenance;
import com.dndmaster.ruleknowledge.application.registration.RulebookRegistrationRepository;
import com.dndmaster.ruleknowledge.application.registration.StoredRulebookRegistration;
import com.dndmaster.ruleknowledge.application.search.EvidenceCandidate;
import com.dndmaster.ruleknowledge.application.search.EvidenceLocationLookupPort;
import com.dndmaster.ruleknowledge.application.search.AuthorizedDocumentScope;
import com.dndmaster.ruleknowledge.application.search.EvidenceSearchResult;
import com.dndmaster.ruleknowledge.application.search.DenseEvidenceSearchService;
import com.dndmaster.ruleknowledge.application.search.RuleEvidenceSearchApplicationService;
import com.dndmaster.ruleknowledge.domain.index.ChunkId;
import com.dndmaster.ruleknowledge.domain.rulebook.DocumentType;
import com.dndmaster.ruleknowledge.domain.rulebook.KnowledgeDocumentId;
import com.dndmaster.ruleknowledge.domain.rulebook.OwnerPlayerId;
import com.dndmaster.ruleknowledge.domain.rulebook.ProcessingStatus;
import com.dndmaster.ruleknowledge.domain.rulebook.RulebookFormat;
import com.dndmaster.ruleknowledge.domain.rulebook.RulebookId;
import com.dndmaster.ruleknowledge.domain.catalog.CatalogRevisionStatus;
import com.dndmaster.ruleknowledge.domain.catalog.RulebookEdition;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class UnifiedEvidenceCandidateSearchControllerTest {
    private static final UUID OWNER = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Test
    void samePageLookupReturnsOnlyTheAuthorizedCurrentVersionAndPreservesSource() throws Exception {
        UUID documentId = UUID.randomUUID();
        String locator = "page=4:chunk=anchor";
        EvidenceLocationLookupPort locationLookup = mock(EvidenceLocationLookupPort.class);
        when(locationLookup.findByLocators(any(), any(), any(), org.mockito.ArgumentMatchers.eq(4)))
                .thenReturn(java.util.Map.of(locator, List.of(new EvidenceCandidate(
                        new KnowledgeDocumentId(documentId), new ChunkId(UUID.randomUUID()), 1, DocumentType.STORYBOOK,
                        "page=4:chunk=neighbor", "same page context", new SourceProvenance(4, List.of(), List.of(), null,
                                "page=4:chunk=neighbor"), null, null))));

        controllerWithLocationLookup(registration(documentId, ProcessingStatus.INDEXED), null, locationLookup)
                .perform(post("/internal/v1/evidence-locations/lookup")
                        .header("X-Internal-Token", "internal-token").contentType(MediaType.APPLICATION_JSON)
                        .content(locationRequest(documentId, locator, 4)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidates[0].locator").value("page=4:chunk=neighbor"))
                .andExpect(jsonPath("$.candidates[0].provenance.pageNumber").value(4))
                .andExpect(jsonPath("$.candidates[0].extractionVersion").value(1));

        ArgumentCaptor<List<AuthorizedDocumentScope>> scope = ArgumentCaptor.forClass(List.class);
        verify(locationLookup).findByLocators(any(), scope.capture(), org.mockito.ArgumentMatchers.eq(List.of(locator)),
                org.mockito.ArgumentMatchers.eq(4));
        assertEquals(1, scope.getValue().getFirst().extractionVersion());
        assertEquals(OWNER, scope.getValue().getFirst().documentOwner().value());
    }

    @Test
    void rejectsWrongExtractionVersionBeforeLocationLookup() throws Exception {
        UUID documentId = UUID.randomUUID();
        EvidenceLocationLookupPort locationLookup = mock(EvidenceLocationLookupPort.class);
        controllerWithLocationLookup(registration(documentId, ProcessingStatus.INDEXED), null, locationLookup)
                .perform(post("/internal/v1/evidence-locations/lookup")
                        .header("X-Internal-Token", "internal-token").contentType(MediaType.APPLICATION_JSON)
                        .content(locationRequest(documentId, "page=4:chunk=anchor", 4).replace("\"extractionVersion\":1", "\"extractionVersion\":2")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EVIDENCE_SEARCH_INVALID_REQUEST"));
        org.mockito.Mockito.verifyNoInteractions(locationLookup);
    }

    @Test
    void authenticatesValidatesScopeAndReturnsCandidateProvenance() throws Exception {
        UUID documentId = UUID.randomUUID();
        DenseEvidenceSearchService denseSearch = mock(DenseEvidenceSearchService.class);
        when(denseSearch.search(any())).thenReturn(new EvidenceSearchResult(List.of(new EvidenceCandidate(
                new KnowledgeDocumentId(documentId), new ChunkId(UUID.randomUUID()), 1, DocumentType.STORYBOOK,
                "page:2", "A hidden passage", new SourceProvenance(2, List.of("Chapter 1"), List.of(), null, "page:2",
                        List.of(new SourceProvenance.SourceSpan(2, 7, 40, 55, null, null, List.of(1d, 2d, 3d, 4d), "p2-table-0", "r1:c2"))),
                1, 0.78))));
        MockMvc mockMvc = controllerWithInternalToken(registration(documentId, ProcessingStatus.INDEXED), denseSearch);

        mockMvc.perform(post("/internal/v1/evidence-candidates/search")
                        .header("X-Internal-Token", "internal-token").contentType(MediaType.APPLICATION_JSON)
                        .content(request(documentId, 5).replace("\"denseLimit\":5", "\"denseLimit\":5,\"minimumDenseSimilarity\":0.6")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownerId").value(OWNER.toString()))
                .andExpect(jsonPath("$.candidates[0].documentId").value(documentId.toString()))
                .andExpect(jsonPath("$.candidates[0].locator").value("page:2"))
                .andExpect(jsonPath("$.candidates[0].provenance.pageNumber").value(2))
                .andExpect(jsonPath("$.candidates[0].provenance.sourceSpans[0].charStart").value(40))
                .andExpect(jsonPath("$.candidates[0].provenance.sourceSpans[0].tableId").value("p2-table-0"))
                .andExpect(jsonPath("$.candidates[0].provenance.sourceSpans[0].tableCell").value("r1:c2"))
                .andExpect(jsonPath("$.candidates[0].denseSimilarity").value(0.78));
        verify(denseSearch).search(any());
        ArgumentCaptor<com.dndmaster.ruleknowledge.application.search.EvidenceSearchRequest> requestCaptor =
                ArgumentCaptor.forClass(com.dndmaster.ruleknowledge.application.search.EvidenceSearchRequest.class);
        verify(denseSearch).search(requestCaptor.capture());
        var captured = requestCaptor.getValue();
        assertEquals(OWNER, captured.ownerPlayerId().value());
        assertEquals(DocumentType.STORYBOOK, captured.scope().getFirst().documentType());
        assertEquals(List.of("page:1"), captured.activeLocators());
        assertEquals("opening", captured.stageKey());
        assertEquals("STORY", captured.actionIntent());
        assertEquals(0.6, captured.minimumDenseSimilarity());
    }

    @Test
    void excludes_previously_seen_immutable_document_evidence_from_the_returned_candidates() throws Exception {
        UUID documentId = UUID.randomUUID();
        DenseEvidenceSearchService denseSearch = mock(DenseEvidenceSearchService.class);
        when(denseSearch.search(any())).thenReturn(new EvidenceSearchResult(List.of(new EvidenceCandidate(
                new KnowledgeDocumentId(documentId), new ChunkId(UUID.randomUUID()), 1, DocumentType.STORYBOOK,
                "page:2", "Previously returned passage", new SourceProvenance(2, List.of(), List.of(), null, "page:2"),
                1,null))));
        String knownId = "IMMUTABLE_DOCUMENT:STORYBOOK:" + documentId + ":1:page:2";

        controllerWithInternalToken(registration(documentId, ProcessingStatus.INDEXED), denseSearch)
                .perform(post("/internal/v1/evidence-candidates/search")
                        .header("X-Internal-Token", "internal-token").contentType(MediaType.APPLICATION_JSON)
                        .content(request(documentId, 5).replace("\"excludedEvidenceIds\":[]",
                                "\"excludedEvidenceIds\":[\"" + knownId + "\"]")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidates").isEmpty());
    }

    @Test
    void rejectsInvalidLimitsWithoutCallingSearch() throws Exception {
        UUID documentId = UUID.randomUUID();
        DenseEvidenceSearchService denseSearch = mock(DenseEvidenceSearchService.class);

        controllerWithInternalToken(registration(documentId, ProcessingStatus.INDEXED), denseSearch).perform(post("/internal/v1/evidence-candidates/search")
                        .header("X-Internal-Token", "internal-token").contentType(MediaType.APPLICATION_JSON)
                        .content(request(documentId, 0)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EVIDENCE_SEARCH_INVALID_REQUEST"));
        org.mockito.Mockito.verifyNoInteractions(denseSearch);
    }

    @Test
    void rejects_minimum_dense_similarity_outside_cosine_range() throws Exception {
        UUID documentId = UUID.randomUUID();
        DenseEvidenceSearchService denseSearch = mock(DenseEvidenceSearchService.class);

        controllerWithInternalToken(registration(documentId, ProcessingStatus.INDEXED), denseSearch)
                .perform(post("/internal/v1/evidence-candidates/search")
                        .header("X-Internal-Token", "internal-token").contentType(MediaType.APPLICATION_JSON)
                        .content(request(documentId, 5).replace("\"denseLimit\":5", "\"denseLimit\":5,\"minimumDenseSimilarity\":1.1")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EVIDENCE_SEARCH_INVALID_REQUEST"));
        org.mockito.Mockito.verifyNoInteractions(denseSearch);
    }

    @Test
    void rejects_unknown_candidate_search_fields() throws Exception {
        UUID documentId = UUID.randomUUID();
        DenseEvidenceSearchService denseSearch = mock(DenseEvidenceSearchService.class);

        controllerWithInternalToken(registration(documentId, ProcessingStatus.INDEXED), denseSearch)
                .perform(post("/internal/v1/evidence-candidates/search")
                        .header("X-Internal-Token", "internal-token").contentType(MediaType.APPLICATION_JSON)
                        .content(request(documentId, 5).replace("\"denseLimit\":5", "\"denseLimit\":5,\"obsoleteLimit\":7")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EVIDENCE_SEARCH_INVALID_REQUEST"));
        org.mockito.Mockito.verifyNoInteractions(denseSearch);
    }

    @Test
    void searchesOnlyThe_server_confirmed_preparation_bundle_scope_without_session_identifiers() throws Exception {
        UUID documentId = UUID.randomUUID();
        UUID bundleId = UUID.randomUUID();
        DenseEvidenceSearchService denseSearch = mock(DenseEvidenceSearchService.class);
        when(denseSearch.search(any())).thenReturn(new EvidenceSearchResult(List.of()));

        controllerWithInternalToken(registration(documentId, ProcessingStatus.INDEXED), denseSearch)
                .perform(post("/internal/v1/evidence-candidates/preparation-search")
                        .header("X-Internal-Token", "internal-token").contentType(MediaType.APPLICATION_JSON)
                        .content(preparationRequest(documentId, bundleId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownerId").value(OWNER.toString()))
                .andExpect(jsonPath("$.scenarioSourceBundleId").value(bundleId.toString()));

        ArgumentCaptor<com.dndmaster.ruleknowledge.application.search.EvidenceSearchRequest> captured =
                ArgumentCaptor.forClass(com.dndmaster.ruleknowledge.application.search.EvidenceSearchRequest.class);
        verify(denseSearch).search(captured.capture());
        assertEquals(OWNER, captured.getValue().ownerPlayerId().value());
        assertEquals(DocumentType.STORYBOOK, captured.getValue().scope().getFirst().documentType());
    }

    @Test
    void acceptsSelectedPublishedCatalogRulebookForPreparationWithoutSessionIdentifiers() throws Exception {
        UUID documentId = UUID.randomUUID();
        UUID bundleId = UUID.randomUUID();
        DenseEvidenceSearchService denseSearch = mock(DenseEvidenceSearchService.class);
        when(denseSearch.search(any())).thenReturn(new EvidenceSearchResult(List.of()));
        RulebookRegistrationRepository registrations = mock(RulebookRegistrationRepository.class);
        UUID catalogOwner = UUID.fromString("00000000-0000-0000-0000-000000000005");
        when(registrations.findById(any())).thenReturn(java.util.Optional.of(registration(
                documentId, ProcessingStatus.INDEXED, catalogOwner, DocumentType.RULEBOOK)));
        CatalogRulebookRepository catalog = mock(CatalogRulebookRepository.class);
        when(catalog.findAll()).thenReturn(List.of(new CatalogRulebookRevision(UUID.randomUUID(), RulebookEdition.DND_5E_2014,
                "공개 룰북", documentId, 1, CatalogRevisionStatus.READY, true, null, Instant.now(), Instant.now())));

        controllerWithInternalToken(registrations, catalog, denseSearch)
                .perform(post("/internal/v1/evidence-candidates/preparation-search")
                        .header("X-Internal-Token", "internal-token").contentType(MediaType.APPLICATION_JSON)
                        .content(preparationRulebookRequest(documentId, bundleId)))
                .andExpect(status().isOk());

        ArgumentCaptor<com.dndmaster.ruleknowledge.application.search.EvidenceSearchRequest> captured =
                ArgumentCaptor.forClass(com.dndmaster.ruleknowledge.application.search.EvidenceSearchRequest.class);
        verify(denseSearch).search(captured.capture());
        assertEquals(DocumentType.RULEBOOK, captured.getValue().scope().getFirst().documentType());
        assertEquals(catalogOwner, captured.getValue().scope().getFirst().documentOwner().value());
        assertEquals(null, captured.getValue().sessionId());
        assertEquals(null, captured.getValue().scenarioPackageId());
    }

    @Test
    void rejects_preparation_search_without_the_internal_token() throws Exception {
        UUID documentId = UUID.randomUUID();
        DenseEvidenceSearchService denseSearch = mock(DenseEvidenceSearchService.class);

        controllerWithInternalToken(registration(documentId, ProcessingStatus.INDEXED), denseSearch)
                .perform(post("/internal/v1/evidence-candidates/preparation-search").contentType(MediaType.APPLICATION_JSON)
                        .content(preparationRequest(documentId, UUID.randomUUID())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("EVIDENCE_SEARCH_UNAUTHENTICATED"));
        org.mockito.Mockito.verifyNoInteractions(denseSearch);
    }

    @Test
    void rejectsMissingAuthenticationWithStableCode() throws Exception {
        UUID documentId = UUID.randomUUID();
        DenseEvidenceSearchService denseSearch = mock(DenseEvidenceSearchService.class);

        controller(registration(documentId, ProcessingStatus.INDEXED), denseSearch)
                .perform(post("/internal/v1/evidence-candidates/search").contentType(MediaType.APPLICATION_JSON)
                        .content(request(documentId, 5)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("EVIDENCE_SEARCH_UNAUTHENTICATED"));
        org.mockito.Mockito.verifyNoInteractions(denseSearch);
    }

    @Test
    void rejects_player_bearer_without_the_internal_service_token() throws Exception {
        UUID documentId = UUID.randomUUID();
        DenseEvidenceSearchService denseSearch = mock(DenseEvidenceSearchService.class);

        controllerWithInternalToken(registration(documentId, ProcessingStatus.INDEXED), denseSearch)
                .perform(post("/internal/v1/evidence-candidates/search")
                        .header("Authorization", "Bearer " + OWNER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(documentId, 5)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("EVIDENCE_SEARCH_UNAUTHENTICATED"));
        org.mockito.Mockito.verifyNoInteractions(denseSearch);
    }

    @Test
    void rejectsForeignDocumentScopeWithStableCode() throws Exception {
        UUID documentId = UUID.randomUUID();
        DenseEvidenceSearchService denseSearch = mock(DenseEvidenceSearchService.class);

        controllerWithInternalToken(registration(documentId, ProcessingStatus.INDEXED, UUID.randomUUID()), denseSearch)
                .perform(post("/internal/v1/evidence-candidates/search").header("X-Internal-Token", "internal-token")
                        .contentType(MediaType.APPLICATION_JSON).content(request(documentId, 5)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EVIDENCE_SEARCH_SCOPE_FORBIDDEN"));
    }

    @Test
    void acceptsCallerConfirmedPublishedCatalogRulebookScope() throws Exception {
        UUID documentId = UUID.randomUUID();
        DenseEvidenceSearchService denseSearch = mock(DenseEvidenceSearchService.class);
        when(denseSearch.search(any())).thenReturn(new EvidenceSearchResult(List.of()));
        RulebookRegistrationRepository registrations = mock(RulebookRegistrationRepository.class);
        when(registrations.findById(any())).thenReturn(java.util.Optional.of(registration(
                documentId, ProcessingStatus.INDEXED, UUID.fromString("00000000-0000-0000-0000-000000000005"), DocumentType.RULEBOOK)));
        CatalogRulebookRepository catalog = mock(CatalogRulebookRepository.class);
        when(catalog.findAll()).thenReturn(List.of(new CatalogRulebookRevision(UUID.randomUUID(), RulebookEdition.DND_5E_2014,
                "공개 룰북", documentId, 1, CatalogRevisionStatus.READY, true, null, Instant.now(), Instant.now())));

        controllerWithInternalToken(registrations, catalog, denseSearch).perform(post("/internal/v1/evidence-candidates/search")
                        .header("X-Internal-Token", "internal-token").contentType(MediaType.APPLICATION_JSON)
                        .content(rulebookRequest(documentId)))
                .andExpect(status().isOk());
        verify(denseSearch).search(any());
        ArgumentCaptor<com.dndmaster.ruleknowledge.application.search.EvidenceSearchRequest> requestCaptor =
                ArgumentCaptor.forClass(com.dndmaster.ruleknowledge.application.search.EvidenceSearchRequest.class);
        verify(denseSearch).search(requestCaptor.capture());
        assertEquals(UUID.fromString("00000000-0000-0000-0000-000000000005"),
                requestCaptor.getValue().scope().getFirst().documentOwner().value());
    }

    @Test
    void rejectsBlankOrDuplicateActiveLocatorsWithoutCallingSearch() throws Exception {
        UUID documentId = UUID.randomUUID();
        DenseEvidenceSearchService denseSearch = mock(DenseEvidenceSearchService.class);

        controllerWithInternalToken(registration(documentId, ProcessingStatus.INDEXED), denseSearch)
                .perform(post("/internal/v1/evidence-candidates/search").header("X-Internal-Token", "internal-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(documentId, 5).replace("[\"page:1\"]", "[\"page:1\",\"page:1\"]")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EVIDENCE_SEARCH_INVALID_REQUEST"));
        org.mockito.Mockito.verifyNoInteractions(denseSearch);
    }

    @Test
    void returnsStableUnavailableErrorWhenBothSearchAttemptsFail() throws Exception {
        UUID documentId = UUID.randomUUID();
        DenseEvidenceSearchService denseSearch = mock(DenseEvidenceSearchService.class);
        when(denseSearch.search(any())).thenThrow(new com.dndmaster.ruleknowledge.application.search.EvidenceSearchUnavailableException(new IllegalStateException("down")));

        controllerWithInternalToken(registration(documentId, ProcessingStatus.INDEXED), denseSearch)
                .perform(post("/internal/v1/evidence-candidates/search").header("X-Internal-Token", "internal-token")
                        .contentType(MediaType.APPLICATION_JSON).content(request(documentId, 5)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("EVIDENCE_SEARCH_UNAVAILABLE"));
    }

    private static MockMvc controller(StoredRulebookRegistration registration, DenseEvidenceSearchService denseSearch) {
        RulebookRegistrationRepository registrations = mock(RulebookRegistrationRepository.class);
        when(registrations.findById(any())).thenReturn(java.util.Optional.of(registration));
        return controller(registrations, null, denseSearch);
    }

    private static MockMvc controllerWithInternalToken(StoredRulebookRegistration registration, DenseEvidenceSearchService denseSearch) {
        RulebookRegistrationRepository registrations = mock(RulebookRegistrationRepository.class);
        when(registrations.findById(any())).thenReturn(java.util.Optional.of(registration));
        return controllerWithInternalToken(registrations, null, denseSearch);
    }

    private static MockMvc controllerWithInternalToken(
            RulebookRegistrationRepository registrations, CatalogRulebookRepository catalog, DenseEvidenceSearchService denseSearch) {
        RuleKnowledgeController controller = new RuleKnowledgeController(mock(RulebookPipelineApplicationService.class), registrations,
                mock(RuleEvidenceSearchApplicationService.class), null, null, null, new com.fasterxml.jackson.databind.ObjectMapper(),
                null, "internal-token", catalog, null, denseSearch);
        return MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(new com.fasterxml.jackson.databind.ObjectMapper())).build();
    }

    private static MockMvc controllerWithLocationLookup(
            StoredRulebookRegistration registration, CatalogRulebookRepository catalog, EvidenceLocationLookupPort locationLookup) {
        RulebookRegistrationRepository registrations = mock(RulebookRegistrationRepository.class);
        when(registrations.findById(any())).thenReturn(java.util.Optional.of(registration));
        return controllerWithLocationLookup(registrations, catalog, locationLookup);
    }

    private static MockMvc controllerWithLocationLookup(
            RulebookRegistrationRepository registrations, CatalogRulebookRepository catalog,
            EvidenceLocationLookupPort locationLookup) {
        RuleKnowledgeController controller = new RuleKnowledgeController(mock(RulebookPipelineApplicationService.class), registrations,
                mock(RuleEvidenceSearchApplicationService.class), null, null, null, new com.fasterxml.jackson.databind.ObjectMapper(),
                null, "internal-token", catalog, null, null, locationLookup);
        return MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(new com.fasterxml.jackson.databind.ObjectMapper())).build();
    }

    private static MockMvc controller(
            RulebookRegistrationRepository registrations, CatalogRulebookRepository catalog, DenseEvidenceSearchService denseSearch) {
        RuleKnowledgeController controller = new RuleKnowledgeController(mock(RulebookPipelineApplicationService.class), registrations,
                mock(RuleEvidenceSearchApplicationService.class), null, null, null, new com.fasterxml.jackson.databind.ObjectMapper(),
                null, "", catalog, null, denseSearch);
        return MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(new com.fasterxml.jackson.databind.ObjectMapper())).build();
    }

    private static String request(UUID documentId, int denseLimit) {
        return """
                {"ownerId":"%s","sessionId":"%s","scenarioPackageId":"%s","stageKey":"opening","actionIntent":"STORY","scope":[{"documentId":"%s","extractionVersion":1,"documentType":"STORYBOOK"}],"activeLocators":["page:1"],"excludedEvidenceIds":[],"query":"where is the passage?","denseLimit":%d}
                """.formatted(OWNER, UUID.randomUUID(), UUID.randomUUID(), documentId, denseLimit);
    }

    private static String rulebookRequest(UUID documentId) {
        return """
                {"ownerId":"%s","sessionId":"%s","scenarioPackageId":"%s","stageKey":"opening","actionIntent":"RULE","scope":[{"documentId":"%s","extractionVersion":1,"documentType":"RULEBOOK"}],"activeLocators":[],"excludedEvidenceIds":[],"query":"what is the rule?","denseLimit":5}
                """.formatted(OWNER, UUID.randomUUID(), UUID.randomUUID(), documentId);
    }

    private static String locationRequest(UUID documentId, String locator, int limit) {
        return """
                {"ownerId":"%s","sessionId":"%s","scenarioPackageId":"%s","scope":[{"documentId":"%s","extractionVersion":1,"documentType":"STORYBOOK"}],"locators":["%s"],"limit":%d}
                """.formatted(OWNER, UUID.randomUUID(), UUID.randomUUID(), documentId, locator, limit);
    }

    private static String preparationRequest(UUID documentId, UUID bundleId) {
        return """
                {"ownerId":"%s","scenarioSourceBundleId":"%s","scope":[{"documentId":"%s","extractionVersion":1,"documentType":"STORYBOOK"}],"activeLocators":["page:1"],"query":"where is the passage?","denseLimit":5}
                """.formatted(OWNER, bundleId, documentId);
    }

    private static String preparationRulebookRequest(UUID documentId, UUID bundleId) {
        return """
                {"ownerId":"%s","scenarioSourceBundleId":"%s","scope":[{"documentId":"%s","extractionVersion":1,"documentType":"RULEBOOK"}],"activeLocators":[],"query":"what is the rule?","denseLimit":5}
                """.formatted(OWNER, bundleId, documentId);
    }

    private static StoredRulebookRegistration registration(UUID id, ProcessingStatus status) { return registration(id, status, OWNER); }
    private static StoredRulebookRegistration registration(UUID id, ProcessingStatus status, UUID owner) {
        return registration(id, status, owner, DocumentType.STORYBOOK);
    }
    private static StoredRulebookRegistration registration(UUID id, ProcessingStatus status, UUID owner, DocumentType documentType) {
        Instant now = Instant.now();
        return new StoredRulebookRegistration(new RulebookId(id), new OwnerPlayerId(owner), "op", "hash", RulebookFormat.TXT,
                1, "storage", status, com.dndmaster.ruleknowledge.domain.rulebook.ExtractionStatus.SUCCESS, "content", List.of(),
                null, 1, now, now, documentType, "story.txt");
    }
}
