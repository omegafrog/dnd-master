package com.dndmaster.ruleknowledge.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dndmaster.ruleknowledge.application.auth.PlayerSessionLookupPort;
import com.dndmaster.ruleknowledge.application.catalog.CatalogRulebookRepository;
import com.dndmaster.ruleknowledge.application.catalog.CatalogRulebookRevision;
import com.dndmaster.ruleknowledge.application.definition.GameSystemDefinitionRepository;
import com.dndmaster.ruleknowledge.application.pipeline.RulebookPipelineApplicationService;
import com.dndmaster.ruleknowledge.application.pipeline.RulebookProcessingResult;
import com.dndmaster.ruleknowledge.application.registration.RulebookRegistrationRepository;
import com.dndmaster.ruleknowledge.application.registration.StoredRulebookRegistration;
import com.dndmaster.ruleknowledge.domain.catalog.CatalogRevisionStatus;
import com.dndmaster.ruleknowledge.domain.catalog.RulebookEdition;
import com.dndmaster.ruleknowledge.domain.rulebook.DocumentType;
import com.dndmaster.ruleknowledge.domain.rulebook.ExtractionStatus;
import com.dndmaster.ruleknowledge.domain.rulebook.OwnerPlayerId;
import com.dndmaster.ruleknowledge.domain.rulebook.ProcessingStatus;
import com.dndmaster.ruleknowledge.domain.rulebook.RulebookFormat;
import com.dndmaster.ruleknowledge.domain.rulebook.RulebookId;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class RulebookCatalogBackofficeControllerTest {
    private static final UUID ADMIN = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID NON_ADMIN = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID CATALOG_OWNER = UUID.fromString("00000000-0000-0000-0000-000000000005");

    @Test
    void backoffice_resolves_session_then_checks_admin_allowlist() throws Exception {
        UUID catalogRulebook = UUID.randomUUID();
        UUID revisionId = UUID.randomUUID();
        CatalogRulebookRepository catalog = mock(CatalogRulebookRepository.class);
        CatalogRulebookRevision revision = new CatalogRulebookRevision(revisionId, RulebookEdition.DND_5E_2014,
                "D&D 5e", catalogRulebook, 1, CatalogRevisionStatus.QUEUED, false, null,
                Instant.now(), Instant.now());
        when(catalog.findAll()).thenReturn(List.of(revision));
        RulebookRegistrationRepository registrations = mock(RulebookRegistrationRepository.class);
        when(registrations.findById(new RulebookId(catalogRulebook))).thenReturn(Optional.of(
                registration(catalogRulebook, ProcessingStatus.INDEXED)));
        GameSystemDefinitionRepository definitions = mock(GameSystemDefinitionRepository.class);
        when(definitions.findPublished(catalogRulebook)).thenReturn(Optional.empty());
        PlayerSessionLookupPort sessions = mock(PlayerSessionLookupPort.class);
        when(sessions.resolvePlayerId("admin-session")).thenReturn(Optional.of(ADMIN));
        when(sessions.resolvePlayerId("player-session")).thenReturn(Optional.of(NON_ADMIN));
        MockMvc mockMvc = controller(catalog, registrations, definitions, sessions);

        mockMvc.perform(post("/api/v1/backoffice/rulebook-catalog/" + revisionId + "/publish"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/backoffice/rulebook-catalog/" + revisionId + "/publish")
                        .header("Authorization", "Bearer " + ADMIN))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/backoffice/rulebook-catalog/" + revisionId + "/publish")
                        .header("Authorization", "Bearer player-session"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/backoffice/rulebook-catalog/" + revisionId + "/publish")
                        .header("Authorization", "Bearer admin-session"))
                .andExpect(status().isOk());
    }

    @Test
    void admin_catalog_list_requires_opaque_admin_session_and_includes_queued_revision() throws Exception {
        UUID queuedRulebook = UUID.randomUUID();
        UUID revisionId = UUID.randomUUID();
        CatalogRulebookRepository catalog = mock(CatalogRulebookRepository.class);
        when(catalog.findAll()).thenReturn(List.of(new CatalogRulebookRevision(revisionId,
                RulebookEdition.DND_5E_2014, "Queued D&D 5e", queuedRulebook, 1,
                CatalogRevisionStatus.QUEUED, false, null, Instant.now(), Instant.now())));
        RulebookRegistrationRepository registrations = mock(RulebookRegistrationRepository.class);
        when(registrations.findById(new RulebookId(queuedRulebook)))
                .thenReturn(Optional.of(registration(queuedRulebook, ProcessingStatus.QUEUED)));
        PlayerSessionLookupPort sessions = mock(PlayerSessionLookupPort.class);
        when(sessions.resolvePlayerId("admin-session")).thenReturn(Optional.of(ADMIN));
        when(sessions.resolvePlayerId("player-session")).thenReturn(Optional.of(NON_ADMIN));
        MockMvc mockMvc = controller(catalog, registrations,
                mock(GameSystemDefinitionRepository.class), sessions);

        mockMvc.perform(get("/api/v1/backoffice/rulebook-catalog"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/backoffice/rulebook-catalog")
                        .header("Authorization", "Bearer " + ADMIN))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/backoffice/rulebook-catalog")
                        .header("Authorization", "Bearer player-session"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/backoffice/rulebook-catalog")
                        .header("Authorization", "Bearer admin-session"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].catalogRevisionId").value(revisionId.toString()))
                .andExpect(jsonPath("$[0].status").value("QUEUED"))
                .andExpect(jsonPath("$[0].processingStatus").value("QUEUED"))
                .andExpect(jsonPath("$[0].published").value(false));
    }

    @Test
    void admin_can_retry_catalog_revision_document() throws Exception {
        UUID rulebookId = UUID.randomUUID();
        UUID revisionId = UUID.randomUUID();
        CatalogRulebookRepository catalog = mock(CatalogRulebookRepository.class);
        when(catalog.findAll()).thenReturn(List.of(revision(revisionId, rulebookId)));
        RulebookRegistrationRepository registrations = mock(RulebookRegistrationRepository.class);
        when(registrations.findById(new RulebookId(rulebookId)))
                .thenReturn(Optional.of(registration(rulebookId, ProcessingStatus.FAILED)));
        PlayerSessionLookupPort sessions = adminSession();
        RulebookPipelineApplicationService pipeline = mock(RulebookPipelineApplicationService.class);
        when(pipeline.retry(new RulebookId(rulebookId)))
                .thenReturn(new RulebookProcessingResult(new RulebookId(rulebookId), ProcessingStatus.QUEUED, List.of()));

        MockMvc mockMvc = controller(catalog, registrations, mock(GameSystemDefinitionRepository.class), sessions, pipeline);

        mockMvc.perform(post("/api/v1/backoffice/rulebook-catalog/" + revisionId + "/retry")
                        .header("Authorization", "Bearer admin-session"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("QUEUED"));
        verify(pipeline).retry(new RulebookId(rulebookId));
    }

    @Test
    void admin_can_retry_selected_catalog_pages_with_layout_selections() throws Exception {
        UUID rulebookId = UUID.randomUUID();
        UUID revisionId = UUID.randomUUID();
        CatalogRulebookRepository catalog = mock(CatalogRulebookRepository.class);
        when(catalog.findAll()).thenReturn(List.of(revision(revisionId, rulebookId)));
        RulebookRegistrationRepository registrations = mock(RulebookRegistrationRepository.class);
        when(registrations.findById(new RulebookId(rulebookId)))
                .thenReturn(Optional.of(registration(rulebookId, ProcessingStatus.NEEDS_REVIEW)));
        PlayerSessionLookupPort sessions = adminSession();
        RulebookPipelineApplicationService pipeline = mock(RulebookPipelineApplicationService.class);
        when(pipeline.retryPages(eq(new RulebookId(rulebookId)), eq("retry-request"), eq(List.of(2)),
                eq(Map.of(2, Map.of("column_count", 2)))))
                .thenReturn(new RulebookProcessingResult(new RulebookId(rulebookId), ProcessingStatus.INDEXED, List.of()));
        MockMvc mockMvc = controller(catalog, registrations, mock(GameSystemDefinitionRepository.class), sessions, pipeline);

        mockMvc.perform(post("/api/v1/backoffice/rulebook-catalog/" + revisionId + "/retry-pages")
                        .header("Authorization", "Bearer admin-session")
                        .contentType("application/json")
                        .content("{\"requestId\":\"retry-request\",\"pages\":[2],\"layoutSelections\":{\"2\":{\"column_count\":2}}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INDEXED"));
        verify(pipeline).retryPages(eq(new RulebookId(rulebookId)), eq("retry-request"), eq(List.of(2)),
                eq(Map.of(2, Map.of("column_count", 2))));
    }

    @Test
    void catalog_retry_requires_an_existing_document_owned_by_catalog() throws Exception {
        UUID rulebookId = UUID.randomUUID();
        UUID revisionId = UUID.randomUUID();
        CatalogRulebookRepository catalog = mock(CatalogRulebookRepository.class);
        when(catalog.findAll()).thenReturn(List.of(revision(revisionId, rulebookId)));
        RulebookRegistrationRepository registrations = mock(RulebookRegistrationRepository.class);
        when(registrations.findById(new RulebookId(rulebookId))).thenReturn(Optional.empty());
        RulebookPipelineApplicationService pipeline = mock(RulebookPipelineApplicationService.class);
        MockMvc mockMvc = controller(catalog, registrations, mock(GameSystemDefinitionRepository.class), adminSession(), pipeline);

        mockMvc.perform(post("/api/v1/backoffice/rulebook-catalog/" + revisionId + "/retry")
                        .header("Authorization", "Bearer admin-session"))
                .andExpect(status().isNotFound());
        verify(pipeline, never()).retry(any());
    }

    @Test
    void catalog_retry_rejects_a_document_owned_by_another_player() throws Exception {
        UUID rulebookId = UUID.randomUUID();
        UUID revisionId = UUID.randomUUID();
        CatalogRulebookRepository catalog = mock(CatalogRulebookRepository.class);
        when(catalog.findAll()).thenReturn(List.of(revision(revisionId, rulebookId)));
        RulebookRegistrationRepository registrations = mock(RulebookRegistrationRepository.class);
        when(registrations.findById(new RulebookId(rulebookId)))
                .thenReturn(Optional.of(registration(rulebookId, NON_ADMIN, ProcessingStatus.FAILED)));
        RulebookPipelineApplicationService pipeline = mock(RulebookPipelineApplicationService.class);
        MockMvc mockMvc = controller(catalog, registrations, mock(GameSystemDefinitionRepository.class), adminSession(), pipeline);

        mockMvc.perform(post("/api/v1/backoffice/rulebook-catalog/" + revisionId + "/retry")
                        .header("Authorization", "Bearer admin-session"))
                .andExpect(status().isForbidden());
        verify(pipeline, never()).retry(any());
    }

    private static MockMvc controller(CatalogRulebookRepository catalog,
            RulebookRegistrationRepository registrations, GameSystemDefinitionRepository definitions,
            PlayerSessionLookupPort sessions) {
        return controller(catalog, registrations, definitions, sessions, mock(RulebookPipelineApplicationService.class));
    }

    private static MockMvc controller(CatalogRulebookRepository catalog,
            RulebookRegistrationRepository registrations, GameSystemDefinitionRepository definitions,
            PlayerSessionLookupPort sessions, RulebookPipelineApplicationService pipeline) {
        var controller = new RulebookCatalogBackofficeController(catalog,
                pipeline, registrations, definitions, sessions, ADMIN.toString());
        return MockMvcBuilders.standaloneSetup(controller).build();
    }

    private static PlayerSessionLookupPort adminSession() {
        PlayerSessionLookupPort sessions = mock(PlayerSessionLookupPort.class);
        when(sessions.resolvePlayerId("admin-session")).thenReturn(Optional.of(ADMIN));
        return sessions;
    }

    private static CatalogRulebookRevision revision(UUID revisionId, UUID rulebookId) {
        return new CatalogRulebookRevision(revisionId, RulebookEdition.DND_5E_2014, "D&D 5e", rulebookId, 1,
                CatalogRevisionStatus.QUEUED, false, null, Instant.now(), Instant.now());
    }

    private static StoredRulebookRegistration registration(UUID id, ProcessingStatus status) {
        return registration(id, CATALOG_OWNER, status);
    }

    private static StoredRulebookRegistration registration(UUID id, UUID owner, ProcessingStatus status) {
        Instant now = Instant.now();
        return new StoredRulebookRegistration(new RulebookId(id), new OwnerPlayerId(owner), "op", "hash",
                RulebookFormat.PDF, 1, "storage", status, ExtractionStatus.SUCCESS, "content", List.of(), null,
                1, now, now, DocumentType.RULEBOOK, "rules.pdf");
    }
}
