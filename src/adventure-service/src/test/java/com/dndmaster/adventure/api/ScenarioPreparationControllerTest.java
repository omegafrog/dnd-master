package com.dndmaster.adventure.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import com.dndmaster.adventure.application.auth.PlayerSessionLookupPort;
import com.dndmaster.adventure.application.scenario.preparation.CharacterCreationBlueprintView;
import com.dndmaster.adventure.application.scenario.preparation.PlayPreparationStatus;
import com.dndmaster.adventure.application.scenario.preparation.PlayPreparationView;
import com.dndmaster.adventure.application.scenario.preparation.RuntimeOptionView;
import com.dndmaster.adventure.application.scenario.preparation.RuntimeOptionsView;
import com.dndmaster.adventure.application.scenario.preparation.ScenarioPreparationApplicationService;
import com.dndmaster.adventure.domain.scenario.OwnerPlayerId;
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

@WebMvcTest(controllers = ScenarioPreparationController.class)
@AutoConfigureMockMvc
@Import({AdventureSecurityConfiguration.class, AuthenticatedPlayerResolver.class})
class ScenarioPreparationControllerTest {
    @Autowired MockMvc mockMvc;

    @MockBean ScenarioPreparationApplicationService service;
    @MockBean PlayerSessionLookupPort playerSessionLookupPort;

    @Test
    void preparationResponseAndOptionsResponseUseAuthenticatedOwner() throws Exception {
        UUID ownerId = UUID.fromString("55555555-5555-5555-5555-555555555555");
        UUID packageId = UUID.fromString("66666666-6666-6666-6666-666666666666");
        when(playerSessionLookupPort.resolvePlayerId("token")).thenReturn(Optional.of(ownerId));
        when(service.read(any(UUID.class), any(OwnerPlayerId.class))).thenReturn(
                new PlayPreparationView(
                        packageId,
                        UUID.fromString("77777777-7777-7777-7777-777777777777"),
                        4L,
                        PlayPreparationStatus.READY,
                        List.of(),
                        new CharacterCreationBlueprintView(
                                true,
                                "CharacterCreationBlueprint: STORYBOOK 1개, RULEBOOK 1개",
                                1,
                                1,
                                List.of("ready"))));
        when(service.runtimeOptions(any(OwnerPlayerId.class))).thenReturn(new RuntimeOptionsView(
                "ollama",
                List.of("search", "move"),
                List.of(
                        new RuntimeOptionView("ollama", "Ollama", true),
                        new RuntimeOptionView("openai", "OpenAI", false)),
                List.of(
                        new RuntimeOptionView("search", "Search", true),
                        new RuntimeOptionView("move", "Move", true))));

        var ownerCaptor = org.mockito.ArgumentCaptor.forClass(OwnerPlayerId.class);

        mockMvc.perform(get("/api/v1/scenario-packages/{scenarioPackageId}/play-preparation", packageId)
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.characterCreationBlueprint.available").value(true))
                .andExpect(jsonPath("$.characterCreationBlueprint.rulebookDocumentCount").value(1))
                .andExpect(jsonPath("$.characterLimit.maximumCharacters").value(1));
        verify(service).read(eq(packageId), ownerCaptor.capture());
        org.junit.jupiter.api.Assertions.assertEquals(ownerId, ownerCaptor.getValue().value());

        mockMvc.perform(get("/api/v1/runtime-options")
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultEngineId").value("ollama"))
                .andExpect(jsonPath("$.engines[0].id").value("ollama"))
                .andExpect(jsonPath("$.tools[0].selectedByDefault").value(true));
    }

    @Test
    void blueprintReviewRequestCarriesExpectedRevisionAndStableNodeId() throws Exception {
        UUID ownerId = UUID.fromString("55555555-5555-5555-5555-555555555555");
        UUID packageId = UUID.fromString("66666666-6666-6666-6666-666666666666");
        when(playerSessionLookupPort.resolvePlayerId("token")).thenReturn(Optional.of(ownerId));
        when(service.resolveBlueprint(eq(packageId), any(OwnerPlayerId.class), eq(7L),
                eq("node-race"), eq("Elf"))).thenReturn(null);

        mockMvc.perform(post("/api/v1/scenario-packages/{scenarioPackageId}/character-blueprint/resolve", packageId)
                        .header("Authorization", "Bearer token")
                        .contentType(APPLICATION_JSON)
                        .content("{\"expectedRevision\":7,\"fieldKey\":\"node-race\",\"value\":\"Elf\"}"))
                .andExpect(status().isOk());
        verify(service).resolveBlueprint(eq(packageId), any(OwnerPlayerId.class), eq(7L), eq("node-race"), eq("Elf"));
    }

    @Test
    void proposalUseCommandCarriesProposalIdAndExpectedRevision() throws Exception {
        UUID ownerId = UUID.fromString("55555555-5555-5555-5555-555555555555");
        UUID packageId = UUID.fromString("66666666-6666-6666-6666-666666666666");
        when(playerSessionLookupPort.resolvePlayerId("token")).thenReturn(Optional.of(ownerId));
        when(service.useStorybookProposal(eq(packageId), any(OwnerPlayerId.class), eq(8L), eq("proposal-1")))
                .thenReturn(new CharacterCreationBlueprintView(true, "review", 1, 1, List.of(), 9,
                        List.of(), "NEEDS_REVIEW", List.of()));

        mockMvc.perform(post("/api/v1/scenario-packages/{scenarioPackageId}/character-blueprint/proposals/{proposalId}/use", packageId, "proposal-1")
                        .header("Authorization", "Bearer token")
                        .contentType(APPLICATION_JSON)
                        .content("{\"expectedRevision\":8}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revision").value(9));

        verify(service).useStorybookProposal(eq(packageId), any(OwnerPlayerId.class), eq(8L), eq("proposal-1"));
    }

    @Test
    void proposalExcludeCommandUsesTheSameOptimisticRevisionBoundary() throws Exception {
        UUID ownerId = UUID.fromString("55555555-5555-5555-5555-555555555555");
        UUID packageId = UUID.fromString("66666666-6666-6666-6666-666666666666");
        when(playerSessionLookupPort.resolvePlayerId("token")).thenReturn(Optional.of(ownerId));
        when(service.excludeStorybookProposal(eq(packageId), any(OwnerPlayerId.class), eq(8L), eq("proposal-1")))
                .thenReturn(new CharacterCreationBlueprintView(true, "review", 1, 1, List.of(), 9,
                        List.of(), "NEEDS_REVIEW", List.of()));

        mockMvc.perform(post("/api/v1/scenario-packages/{scenarioPackageId}/character-blueprint/proposals/{proposalId}/exclude", packageId, "proposal-1")
                        .header("Authorization", "Bearer token")
                        .contentType(APPLICATION_JSON)
                        .content("{\"expectedRevision\":8}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revision").value(9));

        verify(service).excludeStorybookProposal(eq(packageId), any(OwnerPlayerId.class), eq(8L), eq("proposal-1"));
    }

    @Test
    void blueprintDraftEndpointReturnsCharacterSheetTreeForReview() throws Exception {
        UUID ownerId = UUID.fromString("55555555-5555-5555-5555-555555555555");
        UUID packageId = UUID.fromString("66666666-6666-6666-6666-666666666666");
        when(playerSessionLookupPort.resolvePlayerId("token")).thenReturn(Optional.of(ownerId));
        var node = new CharacterCreationBlueprintView.NodeView(
                "node-race", null, "race", "종족", "SINGLE_SELECT", null,
                List.of("Dwarf", "Elf"), List.of(), "EXTRACTED", true, "HIGH",
                "Choose a race", List.of(), List.of(), List.of());
        when(service.generateBlueprintDraft(eq(packageId), any(OwnerPlayerId.class), eq("DND_5E_2014"))).thenReturn(
                new CharacterCreationBlueprintView(true, "draft", 1, 1, List.of(), 2,
                        List.of(), "NEEDS_REVIEW", List.of(node)));

        mockMvc.perform(post("/api/v1/scenario-packages/{scenarioPackageId}/character-blueprint/draft", packageId)
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NEEDS_REVIEW"))
                .andExpect(jsonPath("$.characterSheetTree[0].key").value("race"))
                .andExpect(jsonPath("$.characterSheetTree[0].options[1]").value("Elf"));
        verify(service).generateBlueprintDraft(eq(packageId), any(OwnerPlayerId.class), eq("DND_5E_2014"));
    }
}
