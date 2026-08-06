package com.dndmaster.adventure.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dndmaster.adventure.application.auth.PlayerSessionLookupPort;
import com.dndmaster.adventure.application.combat.AdventureCombatApplicationService;
import com.dndmaster.adventure.application.guidance.RuleGuidanceApplicationService;
import com.dndmaster.adventure.application.runtime.RuntimeTurnApplicationService;
import com.dndmaster.adventure.application.runtime.GmTurnRepository;
import com.dndmaster.adventure.application.runtime.RuntimeTurnRepository;
import com.dndmaster.adventure.application.runtime.SessionEventRepository;
import com.dndmaster.adventure.application.runtime.GmTurnFailureRecorder;
import com.dndmaster.adventure.application.saved.AdventureRepository;
import com.dndmaster.adventure.application.saved.SavedAdventureApplicationService;
import com.dndmaster.adventure.application.scenario.AdventureScenarioApplicationService;
import com.dndmaster.adventure.domain.scenario.AdventureScenario;
import com.dndmaster.adventure.domain.scenario.OwnerPlayerId;
import com.dndmaster.adventure.domain.scenario.ScenarioId;
import com.dndmaster.adventure.domain.scenario.ScenarioPreparationStatus;
import com.dndmaster.adventure.domain.scenario.ScenarioSource;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AdventureController.class)
@AutoConfigureMockMvc
@Import(AdventureSecurityConfiguration.class)
class AdventureControllerTest {
    @Autowired MockMvc mockMvc;

    @MockBean SavedAdventureApplicationService savedAdventureService;
    @MockBean RuntimeTurnApplicationService runtimeTurnService;
    @MockBean AdventureRepository adventureRepository;
    @MockBean GmTurnFailureRecorder gmTurnFailureRecorder;
    @MockBean GmTurnRepository gmTurnRepository;
    @MockBean RuntimeTurnRepository runtimeTurnRepository;
    @MockBean SessionEventRepository sessionEventRepository;
    @MockBean RuleGuidanceApplicationService guidanceService;
    @MockBean AdventureCombatApplicationService combatService;
    @MockBean AdventureScenarioApplicationService scenarioService;
    @MockBean AuthenticatedPlayerResolver playerResolver;
    @MockBean PlayerSessionLookupPort playerSessionLookupPort;

    @Test
    void legacyScenarioUploadReturnsDeprecationMetadata() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID scenarioId = UUID.randomUUID();
        when(playerResolver.playerId()).thenReturn(ownerId);
        when(playerSessionLookupPort.resolvePlayerId(any())).thenReturn(Optional.of(ownerId));
        when(scenarioService.uploadScenario(any())).thenReturn(AdventureScenario.recordUpload(
                new ScenarioId(scenarioId),
                new OwnerPlayerId(ownerId),
                new ScenarioSource("scenarios/legacy", "legacy.pdf", "sha256:legacy")));

        mockMvc.perform(multipart("/api/v1/adventures/scenarios")
                        .file(new MockMultipartFile("file", "legacy.pdf", "application/pdf",
                                "legacy-bytes".getBytes(StandardCharsets.UTF_8)))
                        .header("Authorization", "Bearer " + ownerId))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Deprecation", "true"))
                .andExpect(header().string("Warning", "299 dnd-master \"Legacy one-file scenario upload is deprecated; migrate to bundle/package flows\""))
                .andExpect(header().string("Sunset", "Fri, 31 Dec 2027 00:00:00 GMT"))
                .andExpect(header().string("Link", "</api/v1/adventures/scenario-bundles>; rel=\"alternate\""))
                .andExpect(header().string("X-Legacy-Scenario-Id", scenarioId.toString()));

        var captor = org.mockito.ArgumentCaptor.forClass(com.dndmaster.adventure.application.scenario.ScenarioUpload.class);
        verify(scenarioService).uploadScenario(captor.capture());
        assertThat(captor.getValue().originalFilename()).isEqualTo("legacy.pdf");
        assertThat(captor.getValue().ownerPlayerId()).isEqualTo(new OwnerPlayerId(ownerId));
    }

    @Test
    void resumeUsesAuthenticatedOwner() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID adventureId = UUID.randomUUID();
        when(playerResolver.playerId()).thenReturn(ownerId);
        when(playerSessionLookupPort.resolvePlayerId(any())).thenReturn(Optional.of(ownerId));

        mockMvc.perform(post("/api/v1/adventures/{adventureId}/resume", adventureId)
                        .header("Authorization", "Bearer " + ownerId))
                .andExpect(status().isNoContent());

        verify(savedAdventureService).reopenAdventure(
                new com.dndmaster.adventure.domain.adventure.AdventureId(adventureId),
                new com.dndmaster.adventure.domain.adventure.OwnerPlayerId(ownerId));
    }
}
