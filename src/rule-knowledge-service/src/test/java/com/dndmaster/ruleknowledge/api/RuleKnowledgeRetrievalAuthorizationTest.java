package com.dndmaster.ruleknowledge.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dndmaster.ruleknowledge.application.pipeline.RulebookPipelineApplicationService;
import com.dndmaster.ruleknowledge.application.registration.RulebookRegistrationRepository;
import com.dndmaster.ruleknowledge.application.registration.StoredRulebookRegistration;
import com.dndmaster.ruleknowledge.application.auth.PlayerSessionLookupPort;
import com.dndmaster.ruleknowledge.application.search.RuleEvidenceSearchApplicationService;
import com.dndmaster.ruleknowledge.application.search.StorySourceSearchApplicationService;
import com.dndmaster.ruleknowledge.application.catalog.CatalogRulebookRepository;
import com.dndmaster.ruleknowledge.application.catalog.CatalogRulebookRevision;
import com.dndmaster.ruleknowledge.application.definition.GameSystemDefinitionRepository;
import com.dndmaster.ruleknowledge.domain.catalog.CatalogRevisionStatus;
import com.dndmaster.ruleknowledge.domain.catalog.RulebookEdition;
import com.dndmaster.ruleknowledge.domain.rulebook.DocumentType;
import com.dndmaster.ruleknowledge.domain.rulebook.ProcessingStatus;
import com.dndmaster.ruleknowledge.domain.rulebook.RulebookFormat;
import com.dndmaster.ruleknowledge.domain.rulebook.RulebookId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class RuleKnowledgeRetrievalAuthorizationTest {
    private static final UUID OWNER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID FOREIGN = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID CATALOG_OWNER = UUID.fromString("00000000-0000-0000-0000-000000000005");

    @Test
    void rejects_foreign_rulebook_ids() throws Exception {
        UUID id = UUID.randomUUID();
        MockMvc mockMvc = controllerWith(registration(id, FOREIGN, ProcessingStatus.INDEXED, DocumentType.RULEBOOK));

        mockMvc.perform(post("/internal/v1/rule-evidence/search")
                        .header("X-Internal-Token", "internal-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(id)))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejects_unindexed_rulebook_ids() throws Exception {
        UUID id = UUID.randomUUID();
        MockMvc mockMvc = controllerWith(registration(id, OWNER, ProcessingStatus.PROCESSING, DocumentType.RULEBOOK));

        mockMvc.perform(post("/internal/v1/rule-evidence/search")
                        .header("X-Internal-Token", "internal-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(id)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejects_injected_rulebook_ids_not_registered_for_owner() throws Exception {
        MockMvc mockMvc = controllerWith(null);

        mockMvc.perform(post("/internal/v1/rule-evidence/search")
                        .header("X-Internal-Token", "internal-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(UUID.randomUUID())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejects_missing_internal_token() throws Exception {
        MockMvc mockMvc = controllerWith(null);

        mockMvc.perform(post("/internal/v1/rule-evidence/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(UUID.randomUUID())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void applies_same_owner_and_index_scope_to_story_sources() throws Exception {
        UUID id = UUID.randomUUID();
        MockMvc mockMvc = controllerWith(registration(id, OWNER, ProcessingStatus.INDEXED, DocumentType.STORYBOOK));

        mockMvc.perform(post("/internal/v1/story-sources/search")
                        .header("X-Internal-Token", "internal-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ownerId":"%s","documents":[{"documentId":"%s","extractionVersion":1}],"situation":"find lore","limit":1}
                                """.formatted(OWNER, id)))
                .andExpect(status().isOk());
    }

    @Test
    void accepts_only_published_catalog_rulebook_ids_for_shared_rule_search() throws Exception {
        UUID catalogRulebook = UUID.randomUUID();
        CatalogRulebookRepository catalog = mock(CatalogRulebookRepository.class);
        when(catalog.findAll()).thenReturn(List.of(new CatalogRulebookRevision(
                UUID.randomUUID(), RulebookEdition.DND_5E_2014, "D&D 5e", catalogRulebook, 1,
                CatalogRevisionStatus.READY, true, null, Instant.now(), Instant.now())));
        RulebookRegistrationRepository registrations = mock(RulebookRegistrationRepository.class);
        when(registrations.findById(any())).thenReturn(Optional.of(
                registration(catalogRulebook, CATALOG_OWNER, ProcessingStatus.INDEXED, DocumentType.RULEBOOK)));
        RuleKnowledgeController controller = new RuleKnowledgeController(
                mock(RulebookPipelineApplicationService.class), registrations,
                mock(RuleEvidenceSearchApplicationService.class), storySearch(), null, null,
                new com.fasterxml.jackson.databind.ObjectMapper(), null, "internal-token", catalog);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(new com.fasterxml.jackson.databind.ObjectMapper()))
                .build();
        mockMvc.perform(post("/internal/v1/rule-evidence/search")
                        .header("X-Internal-Token", "internal-token").contentType(MediaType.APPLICATION_JSON)
                        .content(request(catalogRulebook)))
                .andExpect(status().isOk());
    }

    @Test
    void published_catalog_endpoint_exposes_only_ready_published_rulebooks() throws Exception {
        UUID published = UUID.randomUUID();
        UUID unpublished = UUID.randomUUID();
        CatalogRulebookRepository catalog = mock(CatalogRulebookRepository.class);
        when(catalog.findAll()).thenReturn(List.of(
                new CatalogRulebookRevision(UUID.randomUUID(), RulebookEdition.DND_5E_2014, "Published", published, 1,
                        CatalogRevisionStatus.READY, true, null, Instant.now(), Instant.now()),
                new CatalogRulebookRevision(UUID.randomUUID(), RulebookEdition.DND_5E_2014, "Unpublished", unpublished, 1,
                        CatalogRevisionStatus.READY, false, null, Instant.now(), Instant.now())));
        RulebookRegistrationRepository registrations = mock(RulebookRegistrationRepository.class);
        when(registrations.findById(any())).thenAnswer(invocation -> {
            UUID id = ((RulebookId) invocation.getArgument(0)).value();
            return java.util.Optional.of(registration(id, CATALOG_OWNER, ProcessingStatus.INDEXED, DocumentType.RULEBOOK));
        });
        RuleKnowledgeController controller = new RuleKnowledgeController(
                mock(RulebookPipelineApplicationService.class), registrations,
                mock(RuleEvidenceSearchApplicationService.class), storySearch(), null, null,
                new com.fasterxml.jackson.databind.ObjectMapper(), null, "internal-token", catalog);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(new com.fasterxml.jackson.databind.ObjectMapper()))
                .build();

        mockMvc.perform(get("/internal/v1/rulebooks/published-catalog")
                        .header("X-Internal-Token", "internal-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownerId").value(CATALOG_OWNER.toString()))
                .andExpect(jsonPath("$.rulebooks.length()").value(1))
                .andExpect(jsonPath("$.rulebooks[0].knowledgeDocumentId").value(published.toString()));
    }

    @Test
    void generic_owner_lookup_does_not_inject_published_catalog_rulebooks() throws Exception {
        UUID owned = UUID.randomUUID();
        UUID published = UUID.randomUUID();
        CatalogRulebookRepository catalog = mock(CatalogRulebookRepository.class);
        when(catalog.findAll()).thenReturn(List.of(new CatalogRulebookRevision(
                UUID.randomUUID(), RulebookEdition.DND_5E_2014, "Published", published, 1,
                CatalogRevisionStatus.READY, true, null, Instant.now(), Instant.now())));
        RulebookRegistrationRepository registrations = mock(RulebookRegistrationRepository.class);
        when(registrations.findByOwner(any())).thenAnswer(invocation -> {
            com.dndmaster.ruleknowledge.domain.rulebook.OwnerPlayerId requestedOwner = invocation.getArgument(0);
            return OWNER.equals(requestedOwner.value())
                    ? List.of(registration(owned, OWNER, ProcessingStatus.INDEXED, DocumentType.STORYBOOK))
                    : List.of();
        });
        when(registrations.findById(any())).thenReturn(java.util.Optional.of(
                registration(published, CATALOG_OWNER, ProcessingStatus.INDEXED, DocumentType.RULEBOOK)));
        RuleKnowledgeController controller = new RuleKnowledgeController(
                mock(RulebookPipelineApplicationService.class), registrations,
                mock(RuleEvidenceSearchApplicationService.class), storySearch(), null, null,
                new com.fasterxml.jackson.databind.ObjectMapper(), null, "internal-token", catalog);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(new com.fasterxml.jackson.databind.ObjectMapper()))
                .build();

        mockMvc.perform(get("/internal/v1/rulebooks").param("ownerId", OWNER.toString())
                        .header("X-Internal-Token", "internal-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownerId").value(OWNER.toString()))
                .andExpect(jsonPath("$.rulebooks.length()").value(1))
                .andExpect(jsonPath("$.rulebooks[0].knowledgeDocumentId").value(owned.toString()));
        mockMvc.perform(get("/internal/v1/rulebooks").param("ownerId", CATALOG_OWNER.toString())
                        .header("X-Internal-Token", "internal-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownerId").value(CATALOG_OWNER.toString()))
                .andExpect(jsonPath("$.rulebooks.length()").value(0));
    }

    @Test
    void browser_owner_lookup_accepts_the_authenticated_owner() throws Exception {
        UUID owned = UUID.randomUUID();
        RulebookRegistrationRepository registrations = registrationsFor(registration(owned, OWNER, ProcessingStatus.INDEXED, DocumentType.STORYBOOK));

        mockMvcWithOwnerAuthentication(registrations, Optional.of(OWNER))
                .perform(get("/internal/v1/rulebooks").param("ownerId", OWNER.toString())
                        .header("Authorization", "Bearer owner-session"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownerId").value(OWNER.toString()))
                .andExpect(jsonPath("$.rulebooks[0].knowledgeDocumentId").value(owned.toString()));
    }

    @Test
    void browser_owner_lookup_rejects_a_cross_owner_request_without_disclosing_documents() throws Exception {
        UUID owned = UUID.randomUUID();
        RulebookRegistrationRepository registrations = registrationsFor(registration(owned, FOREIGN, ProcessingStatus.INDEXED, DocumentType.STORYBOOK));

        mockMvcWithOwnerAuthentication(registrations, Optional.of(OWNER))
                .perform(get("/internal/v1/rulebooks").param("ownerId", FOREIGN.toString())
                        .header("Authorization", "Bearer owner-session"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("RULEBOOK_LOOKUP_FORBIDDEN"));
    }

    @Test
    void browser_owner_lookup_rejects_missing_or_invalid_credentials() throws Exception {
        MockMvc mockMvc = mockMvcWithOwnerAuthentication(registrationsFor(null), Optional.empty());

        mockMvc.perform(get("/internal/v1/rulebooks").param("ownerId", OWNER.toString()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("RULEBOOK_LOOKUP_UNAUTHENTICATED"));
        mockMvc.perform(get("/internal/v1/rulebooks").param("ownerId", OWNER.toString())
                        .header("Authorization", "Bearer invalid-session"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("RULEBOOK_LOOKUP_UNAUTHENTICATED"));
    }

    @Test
    void internal_owner_lookup_accepts_the_configured_token_without_bearer_credentials() throws Exception {
        UUID owned = UUID.randomUUID();
        RulebookRegistrationRepository registrations = registrationsFor(registration(owned, OWNER, ProcessingStatus.INDEXED, DocumentType.STORYBOOK));

        mockMvcWithOwnerAuthentication(registrations, Optional.empty())
                .perform(get("/internal/v1/rulebooks").param("ownerId", OWNER.toString())
                        .header("X-Internal-Token", "internal-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rulebooks[0].knowledgeDocumentId").value(owned.toString()));
    }

    @Test
    void invalid_internal_token_falls_back_only_to_a_valid_matching_bearer_owner() throws Exception {
        UUID owned = UUID.randomUUID();
        RulebookRegistrationRepository registrations = registrationsFor(registration(owned, OWNER, ProcessingStatus.INDEXED, DocumentType.STORYBOOK));
        MockMvc mockMvc = mockMvcWithOwnerAuthentication(registrations, Optional.of(OWNER));

        mockMvc.perform(get("/internal/v1/rulebooks").param("ownerId", OWNER.toString())
                        .header("X-Internal-Token", "wrong-token")
                        .header("Authorization", "Bearer owner-session"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/internal/v1/rulebooks").param("ownerId", OWNER.toString())
                        .header("X-Internal-Token", "wrong-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("RULEBOOK_LOOKUP_UNAUTHENTICATED"));
        mockMvc.perform(get("/internal/v1/rulebooks").param("ownerId", FOREIGN.toString())
                        .header("X-Internal-Token", "wrong-token")
                        .header("Authorization", "Bearer owner-session"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("RULEBOOK_LOOKUP_FORBIDDEN"));
    }

    @Test
    void internal_indexes_and_ownership_routes_require_the_internal_token() throws Exception {
        UUID id = UUID.randomUUID();
        MockMvc mockMvc = controllerWith(registration(id, OWNER, ProcessingStatus.INDEXED, DocumentType.STORYBOOK));

        mockMvc.perform(get("/internal/v1/rulebook-indexes").param("ownerId", OWNER.toString()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/internal/v1/rulebook-indexes").param("ownerId", OWNER.toString())
                        .header("X-Internal-Token", "wrong-token"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/internal/v1/rulebook-indexes").param("ownerId", OWNER.toString())
                        .header("X-Internal-Token", "internal-token"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/internal/v1/rulebooks/{id}/ownership", id)
                        .param("playerId", OWNER.toString()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/internal/v1/rulebooks/{id}/ownership", id)
                        .param("playerId", OWNER.toString()).header("X-Internal-Token", "internal-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.owned").value(true));
    }

    @Test
    void rejectsUnauthenticatedPublishedCatalogLookup() throws Exception {
        CatalogRulebookRepository catalog = mock(CatalogRulebookRepository.class);
        RuleKnowledgeController controller = new RuleKnowledgeController(
                mock(RulebookPipelineApplicationService.class), mock(RulebookRegistrationRepository.class),
                mock(RuleEvidenceSearchApplicationService.class), storySearch(), null, null,
                new com.fasterxml.jackson.databind.ObjectMapper(), null, "internal-token", catalog);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(new com.fasterxml.jackson.databind.ObjectMapper()))
                .build();

        mockMvc.perform(get("/internal/v1/rulebooks/published-catalog"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("RULEBOOK_CATALOG_UNAUTHENTICATED"));
    }

    @Test
    void rejectsWrongTokenForPublishedCatalogLookup() throws Exception {
        RuleKnowledgeController controller = new RuleKnowledgeController(
                mock(RulebookPipelineApplicationService.class), mock(RulebookRegistrationRepository.class),
                mock(RuleEvidenceSearchApplicationService.class), storySearch(), null, null,
                new com.fasterxml.jackson.databind.ObjectMapper(), null, "internal-token", mock(CatalogRulebookRepository.class));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(new com.fasterxml.jackson.databind.ObjectMapper()))
                .build();

        mockMvc.perform(get("/internal/v1/rulebooks/published-catalog")
                        .header("X-Internal-Token", "wrong-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("RULEBOOK_CATALOG_UNAUTHENTICATED"));
    }

    @Test
    void game_system_definition_routes_require_the_internal_token() throws Exception {
        RuleKnowledgeController controller = new RuleKnowledgeController(
                mock(RulebookPipelineApplicationService.class), mock(RulebookRegistrationRepository.class),
                mock(RuleEvidenceSearchApplicationService.class), storySearch(), null, null,
                new com.fasterxml.jackson.databind.ObjectMapper(), mock(GameSystemDefinitionRepository.class), "internal-token");
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(new com.fasterxml.jackson.databind.ObjectMapper()))
                .build();

        mockMvc.perform(get("/internal/v1/rulebooks/{id}/game-system-definition", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/internal/v1/rulebooks/{id}/game-system-definition", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":1,\"definitionJson\":\"{}\"}"))
                .andExpect(status().isUnauthorized());
    }

    private static MockMvc controllerWith(StoredRulebookRegistration registration) {
        RulebookRegistrationRepository registrations = mock(RulebookRegistrationRepository.class);
        when(registrations.findById(any())).thenReturn(java.util.Optional.ofNullable(registration));
        RuleKnowledgeController controller = new RuleKnowledgeController(
                mock(RulebookPipelineApplicationService.class), registrations,
                mock(RuleEvidenceSearchApplicationService.class), storySearch(), null, null,
                new com.fasterxml.jackson.databind.ObjectMapper(), null, "internal-token", null);
        return MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(new com.fasterxml.jackson.databind.ObjectMapper()))
                .build();
    }

    private static MockMvc mockMvcWithOwnerAuthentication(
            RulebookRegistrationRepository registrations, Optional<UUID> authenticatedOwner) {
        PlayerSessionLookupPort playerSessionLookup = mock(PlayerSessionLookupPort.class);
        when(playerSessionLookup.resolvePlayerId("owner-session")).thenReturn(authenticatedOwner);
        RuleKnowledgeController controller = new RuleKnowledgeController(
                mock(RulebookPipelineApplicationService.class), registrations,
                mock(RuleEvidenceSearchApplicationService.class), storySearch(), null, null,
                new com.fasterxml.jackson.databind.ObjectMapper(), null, "internal-token", mock(CatalogRulebookRepository.class),
                playerSessionLookup);
        return MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(new com.fasterxml.jackson.databind.ObjectMapper()))
                .build();
    }

    private static RulebookRegistrationRepository registrationsFor(StoredRulebookRegistration registration) {
        RulebookRegistrationRepository registrations = mock(RulebookRegistrationRepository.class);
        when(registrations.findByOwner(any())).thenReturn(registration == null ? List.of() : List.of(registration));
        return registrations;
    }

    private static StorySourceSearchApplicationService storySearch() {
        StorySourceSearchApplicationService service = mock(StorySourceSearchApplicationService.class);
        when(service.search(any())).thenReturn(List.of());
        return service;
    }

    private static String request(UUID id) {
        return """
                {"ownerId":"%s","rulebookIds":["%s"],"situation":"find rule","queryIntent":"RULE","limit":1}
                """.formatted(OWNER, id);
    }

    private static StoredRulebookRegistration registration(
            UUID id, UUID owner, ProcessingStatus status, DocumentType type) {
        Instant now = Instant.now();
        return new StoredRulebookRegistration(
                new RulebookId(id), new com.dndmaster.ruleknowledge.domain.rulebook.OwnerPlayerId(owner),
                "op-" + id, "hash-" + id, RulebookFormat.TXT, 1, "storage-" + id, status,
                com.dndmaster.ruleknowledge.domain.rulebook.ExtractionStatus.SUCCESS, "content", List.of(), null,
                1, now, now, type, "document.txt");
    }
}
