package com.dndmaster.ruleknowledge.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dndmaster.ruleknowledge.application.pipeline.RulebookPipelineApplicationService;
import com.dndmaster.ruleknowledge.application.registration.RulebookRegistrationRepository;
import com.dndmaster.ruleknowledge.application.registration.StoredRulebookRegistration;
import com.dndmaster.ruleknowledge.application.search.RuleEvidenceSearchApplicationService;
import com.dndmaster.ruleknowledge.application.search.StorySourceSearchApplicationService;
import com.dndmaster.ruleknowledge.domain.rulebook.DocumentType;
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

class RuleKnowledgeRetrievalAuthorizationTest {
    private static final UUID OWNER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID FOREIGN = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Test
    void rejects_foreign_rulebook_ids() throws Exception {
        UUID id = UUID.randomUUID();
        MockMvc mockMvc = controllerWith(registration(id, FOREIGN, ProcessingStatus.INDEXED, DocumentType.RULEBOOK));

        mockMvc.perform(post("/internal/v1/retrieval/rule-evidence")
                        .header("Authorization", "Bearer " + OWNER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(id)))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejects_unindexed_rulebook_ids() throws Exception {
        UUID id = UUID.randomUUID();
        MockMvc mockMvc = controllerWith(registration(id, OWNER, ProcessingStatus.PROCESSING, DocumentType.RULEBOOK));

        mockMvc.perform(post("/internal/v1/retrieval/rule-evidence")
                        .header("Authorization", "Bearer " + OWNER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(id)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejects_injected_rulebook_ids_not_registered_for_owner() throws Exception {
        MockMvc mockMvc = controllerWith(null);

        mockMvc.perform(post("/internal/v1/retrieval/rule-evidence")
                        .header("Authorization", "Bearer " + OWNER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(UUID.randomUUID())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejects_malformed_bearer_credentials() throws Exception {
        MockMvc mockMvc = controllerWith(null);

        mockMvc.perform(post("/internal/v1/retrieval/rule-evidence")
                        .header("Authorization", "Bearer not-a-uuid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(UUID.randomUUID())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void applies_same_owner_and_index_scope_to_story_sources() throws Exception {
        UUID id = UUID.randomUUID();
        MockMvc mockMvc = controllerWith(registration(id, OWNER, ProcessingStatus.INDEXED, DocumentType.STORYBOOK));

        mockMvc.perform(post("/internal/v1/story-sources/search")
                        .header("Authorization", "Bearer " + OWNER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ownerId":"%s","documents":[{"documentId":"%s","extractionVersion":1}],"situation":"find lore","limit":1}
                                """.formatted(OWNER, id)))
                .andExpect(status().isOk());
    }

    private static MockMvc controllerWith(StoredRulebookRegistration registration) {
        RulebookRegistrationRepository registrations = mock(RulebookRegistrationRepository.class);
        when(registrations.findById(any())).thenReturn(java.util.Optional.ofNullable(registration));
        RuleKnowledgeController controller = new RuleKnowledgeController(
                mock(RulebookPipelineApplicationService.class), registrations,
                mock(RuleEvidenceSearchApplicationService.class), storySearch(), mock(com.fasterxml.jackson.databind.ObjectMapper.class));
        return MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(new com.fasterxml.jackson.databind.ObjectMapper()))
                .build();
    }

    private static StorySourceSearchApplicationService storySearch() {
        StorySourceSearchApplicationService service = mock(StorySourceSearchApplicationService.class);
        when(service.search(any())).thenReturn(List.of());
        return service;
    }

    private static String request(UUID id) {
        return """
                {"ownerId":"%s","documents":[{"documentId":"%s","extractionVersion":1}],"situation":"find rule","queryIntent":"RULE","limit":1}
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
