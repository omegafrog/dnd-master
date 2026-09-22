package com.dndmaster.ruleknowledge.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dndmaster.ruleknowledge.application.auth.PlayerSessionLookupPort;
import com.dndmaster.ruleknowledge.application.catalog.CatalogRulebookRepository;
import com.dndmaster.ruleknowledge.application.catalog.CatalogRulebookRevision;
import com.dndmaster.ruleknowledge.application.definition.GameSystemDefinitionRepository;
import com.dndmaster.ruleknowledge.application.pipeline.RulebookPipelineApplicationService;
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

    private static MockMvc controller(CatalogRulebookRepository catalog,
            RulebookRegistrationRepository registrations, GameSystemDefinitionRepository definitions,
            PlayerSessionLookupPort sessions) {
        var controller = new RulebookCatalogBackofficeController(catalog,
                mock(RulebookPipelineApplicationService.class), registrations, definitions, sessions, ADMIN.toString());
        return MockMvcBuilders.standaloneSetup(controller).build();
    }

    private static StoredRulebookRegistration registration(UUID id, ProcessingStatus status) {
        Instant now = Instant.now();
        return new StoredRulebookRegistration(new RulebookId(id), new OwnerPlayerId(CATALOG_OWNER), "op", "hash",
                RulebookFormat.PDF, 1, "storage", status, ExtractionStatus.SUCCESS, "content", List.of(), null,
                1, now, now, DocumentType.RULEBOOK, "rules.pdf");
    }
}
