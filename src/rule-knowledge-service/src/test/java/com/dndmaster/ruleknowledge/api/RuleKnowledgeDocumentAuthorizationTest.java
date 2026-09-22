package com.dndmaster.ruleknowledge.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dndmaster.ruleknowledge.application.auth.PlayerSessionLookupPort;
import com.dndmaster.ruleknowledge.application.pipeline.RulebookPipelineApplicationService;
import com.dndmaster.ruleknowledge.application.registration.RulebookRegistrationRepository;
import com.dndmaster.ruleknowledge.application.registration.StoredRulebookRegistration;
import com.dndmaster.ruleknowledge.application.search.RuleEvidenceSearchApplicationService;
import com.dndmaster.ruleknowledge.domain.rulebook.DocumentType;
import com.dndmaster.ruleknowledge.domain.rulebook.ExtractionStatus;
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

class RuleKnowledgeDocumentAuthorizationTest {
    private static final UUID OWNER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID FOREIGN = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Test
    void browser_document_routes_require_an_opaque_session_for_the_exact_owner() throws Exception {
        UUID documentId = UUID.randomUUID();
        RulebookRegistrationRepository registrations = mock(RulebookRegistrationRepository.class);
        when(registrations.findById(new RulebookId(documentId))).thenReturn(Optional.of(registration(documentId, OWNER)));
        PlayerSessionLookupPort sessions = mock(PlayerSessionLookupPort.class);
        when(sessions.resolvePlayerId("owner-session")).thenReturn(Optional.of(OWNER));
        when(sessions.resolvePlayerId("foreign-session")).thenReturn(Optional.of(FOREIGN));
        MockMvc mockMvc = controller(registrations, sessions);

        for (var request : documentRequests(documentId)) {
            mockMvc.perform(request.header("Authorization", "Bearer foreign-session"))
                    .andExpect(status().isForbidden());
        }
        for (var request : documentRequests(documentId)) {
            mockMvc.perform(request)
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(get("/api/v1/rulebooks/{id}", documentId)
                        .header("Authorization", "Bearer " + OWNER))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/rulebooks/{id}", documentId)
                        .header("Authorization", "Bearer owner-session"))
                .andExpect(status().isOk());
    }

    private static List<org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder> documentRequests(UUID documentId) {
        return List.of(
                get("/api/v1/rulebooks/{id}", documentId),
                get("/api/v1/rulebooks/{id}/source-preview", documentId),
                post("/api/v1/rulebooks/{id}/retry", documentId),
                post("/api/v1/rulebooks/{id}/retry-pages", documentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestId\":\"retry-1\",\"pages\":[1]}"),
                delete("/api/v1/rulebooks/{id}", documentId));
    }

    private static MockMvc controller(RulebookRegistrationRepository registrations,
            PlayerSessionLookupPort sessions) {
        RuleKnowledgeController controller = new RuleKnowledgeController(
                mock(RulebookPipelineApplicationService.class), registrations,
                mock(RuleEvidenceSearchApplicationService.class), null, null, null,
                new com.fasterxml.jackson.databind.ObjectMapper(), null, "internal-token", null, sessions, null);
        return MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(new com.fasterxml.jackson.databind.ObjectMapper()))
                .build();
    }

    private static StoredRulebookRegistration registration(UUID id, UUID owner) {
        Instant now = Instant.now();
        return new StoredRulebookRegistration(new RulebookId(id), new OwnerPlayerId(owner), "op", "hash",
                RulebookFormat.TXT, 1, "storage", ProcessingStatus.INDEXED, ExtractionStatus.SUCCESS,
                "content", List.of(), null, 1, now, now, DocumentType.STORYBOOK, "story.txt");
    }
}
