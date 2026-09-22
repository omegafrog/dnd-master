package com.dndmaster.system;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dndmaster.ruleknowledge.application.auth.PlayerSessionLookupPort;
import com.dndmaster.ruleknowledge.application.catalog.CatalogRulebookRepository;
import com.dndmaster.ruleknowledge.application.catalog.CatalogRulebookRevision;
import com.dndmaster.ruleknowledge.application.pipeline.RulebookPipelineApplicationService;
import com.dndmaster.ruleknowledge.application.registration.RulebookRegistrationRepository;
import com.dndmaster.ruleknowledge.application.registration.StoredRulebookRegistration;
import com.dndmaster.ruleknowledge.application.search.RuleEvidenceSearchApplicationService;
import com.dndmaster.ruleknowledge.application.search.StorySourceSearchApplicationService;
import com.dndmaster.ruleknowledge.api.RuleKnowledgeController;
import com.dndmaster.ruleknowledge.domain.catalog.CatalogRevisionStatus;
import com.dndmaster.ruleknowledge.domain.catalog.RulebookEdition;
import com.dndmaster.ruleknowledge.domain.rulebook.DocumentType;
import com.dndmaster.ruleknowledge.domain.rulebook.OwnerPlayerId;
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

/** HTTP boundary checks for owner, internal-token, and published-catalog isolation. */
class RuleKnowledgeAuthBoundarySystemTest {
    private static final UUID OWNER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID FOREIGN = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Test
    void browser_cannot_read_another_players_status() throws Exception {
        UUID rulebookId = UUID.randomUUID();
        RulebookRegistrationRepository registrations = mock(RulebookRegistrationRepository.class);
        when(registrations.findById(any())).thenReturn(Optional.of(registration(rulebookId, FOREIGN, DocumentType.STORYBOOK)));
        PlayerSessionLookupPort sessions = mock(PlayerSessionLookupPort.class);
        when(sessions.resolvePlayerId("owner-session")).thenReturn(Optional.of(OWNER));

        mockMvc(registrations, sessions, mock(CatalogRulebookRepository.class))
                .perform(get("/api/v1/rulebooks/{rulebookId}", rulebookId)
                        .header("Authorization", "Bearer owner-session"))
                .andExpect(status().isForbidden());
    }

    @Test
    void internal_search_requires_a_missing_or_correct_shared_token() throws Exception {
        MockMvc mockMvc = mockMvc(mock(RulebookRegistrationRepository.class), mock(PlayerSessionLookupPort.class),
                mock(CatalogRulebookRepository.class));
        String body = """
                {"ownerId":"%s","rulebookIds":["%s"],"situation":"find rule","queryIntent":"RULE","limit":1}
                """.formatted(OWNER, UUID.randomUUID());

        mockMvc.perform(post("/internal/v1/rule-evidence/search")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/internal/v1/rule-evidence/search")
                        .header("X-Internal-Token", "wrong-token")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void unpublished_catalog_rulebook_cannot_be_used_as_shared_search_scope() throws Exception {
        UUID rulebookId = UUID.randomUUID();
        RulebookRegistrationRepository registrations = mock(RulebookRegistrationRepository.class);
        when(registrations.findById(any())).thenReturn(Optional.of(registration(rulebookId,
                UUID.fromString("00000000-0000-0000-0000-000000000005"), DocumentType.RULEBOOK)));
        CatalogRulebookRepository catalog = mock(CatalogRulebookRepository.class);
        when(catalog.findAll()).thenReturn(List.of(new CatalogRulebookRevision(
                UUID.randomUUID(), RulebookEdition.DND_5E_2014, "Not yet published", rulebookId, 1,
                CatalogRevisionStatus.READY, false, null, Instant.now(), Instant.now())));

        String body = """
                {"ownerId":"%s","rulebookIds":["%s"],"situation":"find rule","queryIntent":"RULE","limit":1}
                """.formatted(OWNER, rulebookId);
        mockMvc(registrations, mock(PlayerSessionLookupPort.class), catalog)
                .perform(post("/internal/v1/rule-evidence/search")
                        .header("X-Internal-Token", "internal-token")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }

    private static MockMvc mockMvc(RulebookRegistrationRepository registrations,
            PlayerSessionLookupPort sessions, CatalogRulebookRepository catalog) {
        RuleKnowledgeController controller = new RuleKnowledgeController(
                mock(RulebookPipelineApplicationService.class), registrations,
                mock(RuleEvidenceSearchApplicationService.class), storySearch(), null, null,
                new com.fasterxml.jackson.databind.ObjectMapper(), null, "internal-token", catalog, sessions);
        return MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(new com.fasterxml.jackson.databind.ObjectMapper()))
                .build();
    }

    private static StorySourceSearchApplicationService storySearch() {
        StorySourceSearchApplicationService service = mock(StorySourceSearchApplicationService.class);
        when(service.search(any())).thenReturn(List.of());
        return service;
    }

    private static StoredRulebookRegistration registration(UUID id, UUID owner, DocumentType type) {
        Instant now = Instant.now();
        return new StoredRulebookRegistration(
                new RulebookId(id), new OwnerPlayerId(owner), "op-" + id, "hash-" + id,
                RulebookFormat.TXT, 1, "storage-" + id, ProcessingStatus.INDEXED,
                com.dndmaster.ruleknowledge.domain.rulebook.ExtractionStatus.SUCCESS,
                "content", List.of(), null, 1, now, now, type, "document.txt");
    }
}
