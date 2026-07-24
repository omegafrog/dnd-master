package com.dndmaster.adventure.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dndmaster.adventure.application.combat.AdventureCombatApplicationService;
import com.dndmaster.adventure.application.guidance.RuleGuidanceApplicationService;
import com.dndmaster.adventure.application.runtime.RuntimeTurnApplicationService;
import com.dndmaster.adventure.application.saved.SavedAdventureApplicationService;
import com.dndmaster.adventure.application.scenario.AdventureScenarioApplicationService;
import com.dndmaster.adventure.application.scenario.LegacyScenarioMigrationApplicationService;
import com.dndmaster.adventure.application.scenario.LegacyScenarioNotFoundException;
import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import com.dndmaster.adventure.domain.scenario.ScenarioAccessDeniedException;
import com.dndmaster.adventure.domain.scenario.OwnerPlayerId;
import com.dndmaster.adventure.domain.scenario.ScenarioId;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = LegacyScenarioMigrationController.class)
@AutoConfigureMockMvc
class LegacyScenarioMigrationControllerTest {
    @Autowired MockMvc mockMvc;

    @MockBean SavedAdventureApplicationService savedAdventureService;
    @MockBean RuntimeTurnApplicationService runtimeTurnService;
    @MockBean RuleGuidanceApplicationService guidanceService;
    @MockBean AdventureCombatApplicationService combatService;
    @MockBean AdventureScenarioApplicationService scenarioService;
    @MockBean LegacyScenarioMigrationApplicationService migrationService;

    @Test
    void migrateReturnsBundleAndPackageIds() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID scenarioId = UUID.randomUUID();
        UUID bundleId = UUID.randomUUID();
        UUID packageId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        when(migrationService.migrate(any(ScenarioId.class), any(OwnerPlayerId.class))).thenReturn(
                new LegacyScenarioMigrationApplicationService.LegacyScenarioMigrationResult(
                        new ScenarioId(scenarioId), bundleId, packageId, new KnowledgeDocumentId(documentId),
                        false, false, "legacy.pdf", "legacy scenario migrated"));

        mockMvc.perform(post("/api/v1/adventures/legacy-scenarios/{scenarioId}/migrate", scenarioId)
                        .header("Authorization", "Bearer " + ownerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scenarioId").value(scenarioId.toString()))
                .andExpect(jsonPath("$.bundleId").value(bundleId.toString()))
                .andExpect(jsonPath("$.packageId").value(packageId.toString()))
                .andExpect(jsonPath("$.knowledgeDocumentId").value(documentId.toString()))
                .andExpect(jsonPath("$.requiresReupload").value(false))
                .andExpect(jsonPath("$.reupload").value(false));
    }

    @Test
    void reuploadReturnsReuploadStatus() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID scenarioId = UUID.randomUUID();
        when(migrationService.reupload(any(ScenarioId.class), any(OwnerPlayerId.class), any())).thenReturn(
                new LegacyScenarioMigrationApplicationService.LegacyScenarioMigrationResult(
                        new ScenarioId(scenarioId), UUID.randomUUID(), UUID.randomUUID(),
                        new KnowledgeDocumentId(UUID.randomUUID()), true, false, "replacement.pdf",
                        "legacy scenario reupload migrated"));

        mockMvc.perform(multipart("/api/v1/adventures/legacy-scenarios/{scenarioId}/reupload", scenarioId)
                        .file(new MockMultipartFile("file", "replacement.pdf", "application/pdf",
                                "replacement".getBytes(StandardCharsets.UTF_8)))
                        .header("Authorization", "Bearer " + ownerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reupload").value(true));
    }

    @Test
    void migrateReturnsNotFoundWhenScenarioMissing() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID scenarioId = UUID.randomUUID();
        when(migrationService.migrate(any(ScenarioId.class), any(OwnerPlayerId.class)))
                .thenThrow(new LegacyScenarioNotFoundException());

        mockMvc.perform(post("/api/v1/adventures/legacy-scenarios/{scenarioId}/migrate", scenarioId)
                        .header("Authorization", "Bearer " + ownerId))
                .andExpect(status().isNotFound());
    }

    @Test
    void migrateReturnsForbiddenWhenScenarioOwnershipMismatch() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID scenarioId = UUID.randomUUID();
        when(migrationService.migrate(any(ScenarioId.class), any(OwnerPlayerId.class)))
                .thenThrow(new ScenarioAccessDeniedException());

        mockMvc.perform(post("/api/v1/adventures/legacy-scenarios/{scenarioId}/migrate", scenarioId)
                        .header("Authorization", "Bearer " + ownerId))
                .andExpect(status().isForbidden());
    }
}
