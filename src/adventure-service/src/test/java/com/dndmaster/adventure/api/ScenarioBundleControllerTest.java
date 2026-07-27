package com.dndmaster.adventure.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dndmaster.adventure.application.auth.PlayerSessionLookupPort;
import com.dndmaster.adventure.application.knowledge.KnowledgeDocumentStatus;
import com.dndmaster.adventure.application.scenario.BundleDocumentDraft;
import com.dndmaster.adventure.application.scenario.ScenarioBundleApplicationService;
import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import com.dndmaster.adventure.domain.scenario.OwnerPlayerId;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleDocumentRole;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleId;
import com.dndmaster.adventure.domain.scenario.ScenarioSourceBundle;
import com.dndmaster.adventure.domain.scenario.ScenarioSourceBundleRevision;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleDocumentSelection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = ScenarioBundleController.class)
@AutoConfigureMockMvc
@Import(AdventureSecurityConfiguration.class)
class ScenarioBundleControllerTest {
    @Autowired MockMvc mockMvc;

    @MockBean ScenarioBundleApplicationService service;
    @MockBean AuthenticatedPlayerResolver playerResolver;
    @MockBean PlayerSessionLookupPort playerSessionLookupPort;

    @Test
    void createBundleUsesResolvedOwnerRatherThanParsingBearerAsUuid() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        UUID bundleId = UUID.randomUUID();
        when(playerResolver.playerId()).thenReturn(ownerId);
        when(playerSessionLookupPort.resolvePlayerId(any())).thenReturn(Optional.of(ownerId));
        when(service.createBundle(any(), any())).thenReturn(bundle(bundleId, ownerId, documentId));

        mockMvc.perform(post("/api/v1/adventures/scenario-bundles")
                        .header("Authorization", "Bearer opaque-session-token")
                        .contentType("application/json")
                        .content("""
                                {
                                  "playerId":"%s",
                                  "documents":[
                                    {"knowledgeDocumentId":"%s","role":"MAIN_SCENARIO"}
                                  ]
                                }
                                """.formatted(ownerId, documentId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bundleId").value(bundleId.toString()))
                .andExpect(jsonPath("$.ownerPlayerId").value(ownerId.toString()))
                .andExpect(jsonPath("$.documents[0].knowledgeDocumentId").value(documentId.toString()))
                .andExpect(jsonPath("$.documents[0].role").value("MAIN_SCENARIO"));
    }

    private static ScenarioSourceBundle bundle(UUID bundleId, UUID ownerId, UUID documentId) {
        return ScenarioSourceBundle.create(
                new ScenarioBundleId(bundleId),
                new OwnerPlayerId(ownerId),
                new ScenarioSourceBundleRevision(1, List.of(
                        new ScenarioBundleDocumentSelection(
                                new KnowledgeDocumentId(documentId),
                                ScenarioBundleDocumentRole.MAIN_SCENARIO,
                                KnowledgeDocumentStatus.INDEXED,
                                "main.pdf",
                                "STORYBOOK",
                                3L))));
    }
}
