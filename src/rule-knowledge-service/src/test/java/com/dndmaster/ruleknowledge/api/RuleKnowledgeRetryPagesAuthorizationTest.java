package com.dndmaster.ruleknowledge.api;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dndmaster.ruleknowledge.application.auth.PlayerSessionLookupPort;
import com.dndmaster.ruleknowledge.application.pipeline.RulebookPipelineApplicationService;
import com.dndmaster.ruleknowledge.application.registration.RulebookRegistrationRepository;
import com.dndmaster.ruleknowledge.application.registration.StoredRulebookRegistration;
import com.dndmaster.ruleknowledge.application.search.RuleEvidenceSearchApplicationService;
import com.dndmaster.ruleknowledge.domain.rulebook.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class RuleKnowledgeRetryPagesAuthorizationTest {
    private static final UUID OWNER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ADMIN = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID DOCUMENT = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @Test
    void manual_confirmation_requires_admin_source_confirmation_and_current_candidate() throws Exception {
        RulebookPipelineApplicationService pipeline = mock(RulebookPipelineApplicationService.class);
        MockMvc mvc = mvc(pipeline, sessions());

        mvc.perform(request("owner-session", "ev-stale", true, true))
                .andExpect(status().isForbidden());
        mvc.perform(request(null, "ev-current", true, true))
                .andExpect(status().isUnauthorized());
        mvc.perform(request("admin-session", "ev-current", true, false))
                .andExpect(status().isBadRequest());
        mvc.perform(request("admin-session", "ev-stale", true, true))
                .andExpect(status().isConflict());
        verifyNoInteractions(pipeline);

        mvc.perform(request("admin-session", "ev-current", true, true))
                .andExpect(status().isOk());
        verify(pipeline).retryPages(eq(new RulebookId(DOCUMENT)), eq("retry-1"), eq(List.of(2)),
                anyMap(), eq("ev-current"), eq(ADMIN));
    }

    @Test
    void owner_can_retry_without_manual_layout_selection_against_current_candidate() throws Exception {
        RulebookPipelineApplicationService pipeline = mock(RulebookPipelineApplicationService.class);
        mvc(pipeline, sessions()).perform(post("/api/v1/rulebooks/{id}/retry-pages", DOCUMENT)
                        .header("Authorization", "Bearer owner-session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestId\":\"retry-1\",\"pages\":[2],\"candidateExtractionVersion\":\"ev-current\"}"))
                .andExpect(status().isOk());
        verify(pipeline).retryPages(eq(new RulebookId(DOCUMENT)), eq("retry-1"), eq(List.of(2)),
                eq(java.util.Map.of()), eq("ev-current"), isNull());
    }

    @Test
    void internal_rulebook_summary_exposes_candidate_version_and_accepts_non_uuid_admin_marker() throws Exception {
        mvc(mock(RulebookPipelineApplicationService.class), sessions())
                .perform(get("/internal/v1/rulebooks").param("ownerId", OWNER.toString())
                        .header("X-Internal-Token", "internal-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rulebooks[0].candidateExtractionVersion").value("ev-current"));
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request(
            String token, String candidate, boolean manual, boolean confirmed) {
        var request = post("/api/v1/rulebooks/{id}/retry-pages", DOCUMENT).contentType(MediaType.APPLICATION_JSON)
                .content("{\"requestId\":\"retry-1\",\"pages\":[2],\"layoutSelections\":"
                        + (manual ? "{\"2\":{\"columns\":2}}" : "{}")
                        + ",\"candidateExtractionVersion\":\"" + candidate + "\",\"confirmedAgainstSource\":" + confirmed + "}");
        return token == null ? request : request.header("Authorization", "Bearer " + token);
    }

    private static PlayerSessionLookupPort sessions() {
        PlayerSessionLookupPort sessions = mock(PlayerSessionLookupPort.class);
        when(sessions.resolvePlayerId("owner-session")).thenReturn(Optional.of(OWNER));
        when(sessions.resolvePlayerId("admin-session")).thenReturn(Optional.of(ADMIN));
        return sessions;
    }

    private static MockMvc mvc(RulebookPipelineApplicationService pipeline, PlayerSessionLookupPort sessions) {
        RulebookRegistrationRepository registrations = mock(RulebookRegistrationRepository.class);
        when(registrations.findById(new RulebookId(DOCUMENT))).thenReturn(Optional.of(registration()));
        RuleKnowledgeController controller = new RuleKnowledgeController(pipeline, registrations,
                mock(RuleEvidenceSearchApplicationService.class), null, null, null, new ObjectMapper(), null,
                "internal-token", null, sessions, null);
        when(registrations.findByOwner(new OwnerPlayerId(OWNER))).thenReturn(List.of(registration()));
        controller.setBackofficeAdminPlayerIds("local-catalog-admin," + ADMIN);
        return MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(new ObjectMapper())).build();
    }

    private static StoredRulebookRegistration registration() {
        Instant now = Instant.now();
        return new StoredRulebookRegistration(new RulebookId(DOCUMENT), new OwnerPlayerId(OWNER), "op", "hash",
                RulebookFormat.TXT, 1, "storage", ProcessingStatus.NEEDS_REVIEW, ExtractionStatus.SUCCESS,
                "content", List.of(), null, 1, now, now, DocumentType.RULEBOOK, "rules.txt", "content",
                List.of(), List.of(), List.of(), null, "ev-current", null, null, List.of());
    }
}
