package com.dndmaster.ruleknowledge.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dndmaster.ruleknowledge.application.catalog.CatalogRulebookRepository;
import com.dndmaster.ruleknowledge.application.pipeline.RulebookPipelineApplicationService;
import com.dndmaster.ruleknowledge.application.registration.RulebookRegistrationRepository;
import com.dndmaster.ruleknowledge.application.search.EvidenceCandidate;
import com.dndmaster.ruleknowledge.application.search.EvidenceSearchResult;
import com.dndmaster.ruleknowledge.application.search.HybridEvidenceSearchService;
import com.dndmaster.ruleknowledge.application.search.CharacterContextSearchApplicationService;
import com.dndmaster.ruleknowledge.application.search.RuleEvidenceSearchApplicationService;
import com.dndmaster.ruleknowledge.application.search.StorySourceSearchApplicationService;
import com.dndmaster.ruleknowledge.application.publication.SourceProvenance;
import com.dndmaster.ruleknowledge.domain.index.ChunkId;
import com.dndmaster.ruleknowledge.domain.rulebook.DocumentType;
import com.dndmaster.ruleknowledge.domain.rulebook.KnowledgeDocumentId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class RuleKnowledgeCharacterContextHybridTest {
    @Test
    void character_context_uses_fused_candidates_when_hybrid_search_is_configured() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID document = UUID.randomUUID();
        RulebookRegistrationRepository registrations = mock(RulebookRegistrationRepository.class);
        when(registrations.findById(any())).thenReturn(java.util.Optional.of(
                new com.dndmaster.ruleknowledge.application.registration.StoredRulebookRegistration(
                        new com.dndmaster.ruleknowledge.domain.rulebook.RulebookId(document),
                        new com.dndmaster.ruleknowledge.domain.rulebook.OwnerPlayerId(owner), "op", "hash",
                        com.dndmaster.ruleknowledge.domain.rulebook.RulebookFormat.TXT, 1, "storage",
                        com.dndmaster.ruleknowledge.domain.rulebook.ProcessingStatus.INDEXED,
                        com.dndmaster.ruleknowledge.domain.rulebook.ExtractionStatus.SUCCESS, "content", List.of(), null,
                        1, java.time.Instant.now(), java.time.Instant.now(), DocumentType.STORYBOOK, "story.txt")));
        HybridEvidenceSearchService hybrid = mock(HybridEvidenceSearchService.class);
        when(hybrid.search(any())).thenReturn(new EvidenceSearchResult(List.of(new EvidenceCandidate(
                new KnowledgeDocumentId(document), new ChunkId(UUID.randomUUID()), 1, DocumentType.STORYBOOK,
                "page:2", "story constraint", new SourceProvenance(2, List.of(), List.of(), null, "page:2"),
                1, 1, 0.032d))));

        RuleKnowledgeController controller = new RuleKnowledgeController(
                mock(RulebookPipelineApplicationService.class), registrations,
                mock(RuleEvidenceSearchApplicationService.class), mock(StorySourceSearchApplicationService.class),
                mock(CharacterContextSearchApplicationService.class), null,
                new com.fasterxml.jackson.databind.ObjectMapper(), null, "internal-token",
                mock(CatalogRulebookRepository.class), null, hybrid);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(new com.fasterxml.jackson.databind.ObjectMapper()))
                .build();

        mockMvc.perform(post("/internal/v1/character-context/search")
                        .header("X-Internal-Token", "internal-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ownerId":"%s","documents":[{"documentId":"%s","documentType":"STORYBOOK","extractionVersion":1}],"situation":"find constraints"}
                                """.formatted(owner, document)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evidence[0].knowledgeDocumentId").value(document.toString()))
                .andExpect(jsonPath("$.evidence[0].excerpt").value("story constraint"));
    }

    @Test
    void character_context_authorizes_catalog_rulebook_and_owned_storybook_independently() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID catalogRulebook = UUID.randomUUID();
        UUID storybook = UUID.randomUUID();
        RulebookRegistrationRepository registrations = mock(RulebookRegistrationRepository.class);
        when(registrations.findById(any())).thenAnswer(invocation -> {
            UUID id = invocation.getArgument(0, com.dndmaster.ruleknowledge.domain.rulebook.RulebookId.class).value();
            return java.util.Optional.of(registration(id, id.equals(catalogRulebook)
                    ? UUID.fromString("00000000-0000-0000-0000-000000000005") : owner,
                    id.equals(catalogRulebook) ? DocumentType.RULEBOOK : DocumentType.STORYBOOK));
        });
        CatalogRulebookRepository catalog = mock(CatalogRulebookRepository.class);
        when(catalog.findAll()).thenReturn(List.of(new com.dndmaster.ruleknowledge.application.catalog.CatalogRulebookRevision(
                UUID.randomUUID(), com.dndmaster.ruleknowledge.domain.catalog.RulebookEdition.DND_5E_2014,
                "공용 규칙", catalogRulebook, 1,
                com.dndmaster.ruleknowledge.domain.catalog.CatalogRevisionStatus.READY, true, null,
                java.time.Instant.now(), java.time.Instant.now())));
        HybridEvidenceSearchService hybrid = mock(HybridEvidenceSearchService.class);
        when(hybrid.search(any())).thenReturn(new EvidenceSearchResult(List.of()));

        RuleKnowledgeController controller = new RuleKnowledgeController(
                mock(RulebookPipelineApplicationService.class), registrations,
                mock(RuleEvidenceSearchApplicationService.class), mock(StorySourceSearchApplicationService.class),
                mock(CharacterContextSearchApplicationService.class), null,
                new com.fasterxml.jackson.databind.ObjectMapper(), null, "internal-token", catalog, null, hybrid);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(new com.fasterxml.jackson.databind.ObjectMapper()))
                .build();

        mockMvc.perform(post("/internal/v1/character-context/search")
                        .header("X-Internal-Token", "internal-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ownerId":"%s","documents":[
                                  {"documentId":"%s","documentType":"RULEBOOK","extractionVersion":1},
                                  {"documentId":"%s","documentType":"STORYBOOK","extractionVersion":1}
                                ],"situation":"character creation choices"}
                                """.formatted(owner, catalogRulebook, storybook)))
                .andExpect(status().isOk());

        ArgumentCaptor<com.dndmaster.ruleknowledge.application.search.EvidenceSearchRequest> request =
                ArgumentCaptor.forClass(com.dndmaster.ruleknowledge.application.search.EvidenceSearchRequest.class);
        verify(hybrid).search(request.capture());
        var scope = request.getValue().scope();
        org.junit.jupiter.api.Assertions.assertEquals(
                UUID.fromString("00000000-0000-0000-0000-000000000005"), scope.get(0).documentOwner().value());
        org.junit.jupiter.api.Assertions.assertEquals(owner, scope.get(1).documentOwner().value());
        org.junit.jupiter.api.Assertions.assertEquals(owner, request.getValue().ownerPlayerId().value());
    }

    private static com.dndmaster.ruleknowledge.application.registration.StoredRulebookRegistration registration(
            UUID document, UUID owner, DocumentType type) {
        return new com.dndmaster.ruleknowledge.application.registration.StoredRulebookRegistration(
                new com.dndmaster.ruleknowledge.domain.rulebook.RulebookId(document),
                new com.dndmaster.ruleknowledge.domain.rulebook.OwnerPlayerId(owner), "op", "hash",
                com.dndmaster.ruleknowledge.domain.rulebook.RulebookFormat.TXT, 1, "storage",
                com.dndmaster.ruleknowledge.domain.rulebook.ProcessingStatus.INDEXED,
                com.dndmaster.ruleknowledge.domain.rulebook.ExtractionStatus.SUCCESS, "content", List.of(), null,
                1, java.time.Instant.now(), java.time.Instant.now(), type, "document.txt");
    }
}
