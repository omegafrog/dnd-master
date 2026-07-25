package com.dndmaster.adventure.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
}
