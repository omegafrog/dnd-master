package com.dndmaster.character.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dndmaster.character.application.CharacterSheetApplicationService;
import com.dndmaster.character.application.CharacterSheetsDeletionConsumer;
import com.dndmaster.character.domain.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = CharacterSheetController.class)
@AutoConfigureMockMvc
class CharacterSheetControllerTest {
    @Autowired MockMvc mockMvc;

    @MockBean CharacterSheetApplicationService service;
    @MockBean ApiRequestGuard requestGuard;
    @MockBean CharacterSheetsDeletionConsumer deletionConsumer;

    @Test
    void createsAndReturnsCharacterSheetWithoutUserFacingUuidInput() throws Exception {
        UUID sheetId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID adventureId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        CharacterSheet sheet = new CharacterSheet(
                new CharacterSheetId(sheetId),
                new AdventureId(adventureId),
                SheetEdition.DND_5E_2024,
                new CharacterSheetData2024("Aria", 3, true));
        when(service.createSheet(any())).thenReturn(sheet);

        mockMvc.perform(post("/internal/v1/adventure-sessions/{sessionId}/character-sheets", adventureId)
                        .contentType("application/json")
                        .content("""
                                {
                                  "edition": "DND_5E_2024",
                                  "characterName": "Aria",
                                  "level": 3,
                                  "inspiration": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.characterSheetId").value(sheetId.toString()))
                .andExpect(jsonPath("$.adventureId").value(adventureId.toString()))
                .andExpect(jsonPath("$.edition").value("DND_5E_2024"))
                .andExpect(jsonPath("$.characterName").value("Aria"))
                .andExpect(jsonPath("$.level").value(3))
                .andExpect(jsonPath("$.inspiration").value(true));
        var captor = org.mockito.ArgumentCaptor.forClass(com.dndmaster.character.application.CreateCharacterSheetCommand.class);
        verify(service).createSheet(captor.capture());
        org.junit.jupiter.api.Assertions.assertEquals(SheetEdition.DND_5E_2024, captor.getValue().requestedEdition());
        org.junit.jupiter.api.Assertions.assertEquals("Aria", captor.getValue().data().characterName());
        org.junit.jupiter.api.Assertions.assertEquals(3, captor.getValue().data().level());
        org.junit.jupiter.api.Assertions.assertEquals(true, ((CharacterSheetData2024) captor.getValue().data()).heroicInspiration());
        org.junit.jupiter.api.Assertions.assertNotNull(captor.getValue().adventureId().value());
    }

    @Test
    void mapsNestedBlueprintValuesIntoStartingAbilities() throws Exception {
        UUID adventureId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        CharacterSheet sheet = new CharacterSheet(
                new CharacterSheetId(UUID.fromString("11111111-1111-1111-1111-111111111111")),
                new AdventureId(adventureId), SheetEdition.DND_5E_2024,
                new CharacterSheetData2024("Aria", 1, false, "", "", "", ""));
        when(service.createSheet(any())).thenReturn(sheet);

        mockMvc.perform(post("/internal/v1/adventure-sessions/{sessionId}/character-sheets", adventureId)
                        .contentType("application/json")
                        .content("""
                                {"edition":"DND_5E_2024","characterName":"Aria","level":1,
                                 "blueprintValues":{"node-str":"12","starting_ability_scores.con":"14"}}
                                """))
                .andExpect(status().isOk());

        var captor = org.mockito.ArgumentCaptor.forClass(com.dndmaster.character.application.CreateCharacterSheetCommand.class);
        verify(service).createSheet(captor.capture());
        org.junit.jupiter.api.Assertions.assertEquals("con=14", ((CharacterSheetData2024) captor.getValue().data()).startingAbilities());
    }

    @Test
    void keepsDerivedStatisticsWithTheCharacterSheet() throws Exception {
        UUID adventureId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        CharacterSheet sheet = new CharacterSheet(
                new CharacterSheetId(UUID.fromString("11111111-1111-1111-1111-111111111111")),
                new AdventureId(adventureId), SheetEdition.DND_5E_2024,
                new CharacterSheetData2024("Aria", 1, false, "Elf", "Wizard", "Sage", "dexterity=15", "{\"speed\":30}"));
        when(service.createSheet(any())).thenReturn(sheet);

        mockMvc.perform(post("/internal/v1/adventure-sessions/{sessionId}/character-sheets", adventureId)
                        .contentType("application/json")
                        .content("""
                                {"edition":"DND_5E_2024","characterName":"Aria","level":1,
                                 "derivedStatistics":"{\\"speed\\":30}"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.derivedStatistics").value("{\"speed\":30}"));

        var captor = org.mockito.ArgumentCaptor.forClass(com.dndmaster.character.application.CreateCharacterSheetCommand.class);
        verify(service).createSheet(captor.capture());
        org.junit.jupiter.api.Assertions.assertEquals("{\"speed\":30}", captor.getValue().data().derivedStatistics());
    }

    @Test
    void initializesRuntimeHitPointsFromMaximumWhenCreationOmitsThem() throws Exception {
        UUID adventureId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        CharacterSheet sheet = new CharacterSheet(new CharacterSheetId(UUID.fromString("11111111-1111-1111-1111-111111111111")),
                new AdventureId(adventureId), SheetEdition.DND_5E_2024, new CharacterSheetData2024("Aria", 1, false));
        when(service.createSheet(any())).thenReturn(sheet);

        mockMvc.perform(post("/internal/v1/adventure-sessions/{sessionId}/character-sheets", adventureId)
                        .contentType("application/json")
                        .content("""
                                {"edition":"DND_5E_2024","characterName":"Aria","level":1,
                                 "derivedStatistics":"{\\"hitPointMaximum\\":12}"}
                                """))
                .andExpect(status().isOk());

        var captor = org.mockito.ArgumentCaptor.forClass(com.dndmaster.character.application.CreateCharacterSheetCommand.class);
        verify(service).createSheet(captor.capture());
        org.junit.jupiter.api.Assertions.assertEquals(12,
                new com.fasterxml.jackson.databind.ObjectMapper().readTree(captor.getValue().data().characterState()).path("currentHitPoints").asInt());
    }

    @Test
    void preservesExplicitZeroRuntimeHitPointsOnCreation() throws Exception {
        UUID adventureId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        CharacterSheet sheet = new CharacterSheet(new CharacterSheetId(UUID.fromString("11111111-1111-1111-1111-111111111111")),
                new AdventureId(adventureId), SheetEdition.DND_5E_2024, new CharacterSheetData2024("Aria", 1, false));
        when(service.createSheet(any())).thenReturn(sheet);

        mockMvc.perform(post("/internal/v1/adventure-sessions/{sessionId}/character-sheets", adventureId)
                        .contentType("application/json")
                        .content("""
                                {"edition":"DND_5E_2024","characterName":"Aria","level":1,
                                 "derivedStatistics":"{\\"hitPointMaximum\\":12}","characterState":"{\\"currentHitPoints\\":0}"}
                                """))
                .andExpect(status().isOk());

        var captor = org.mockito.ArgumentCaptor.forClass(com.dndmaster.character.application.CreateCharacterSheetCommand.class);
        verify(service).createSheet(captor.capture());
        org.junit.jupiter.api.Assertions.assertEquals(0,
                new com.fasterxml.jackson.databind.ObjectMapper().readTree(captor.getValue().data().characterState()).path("currentHitPoints").asInt());
    }

    @Test
    void doesNotTreatTextContainingHitPointFieldAsInitializedState() throws Exception {
        UUID adventureId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        CharacterSheet sheet = new CharacterSheet(new CharacterSheetId(UUID.fromString("11111111-1111-1111-1111-111111111111")),
                new AdventureId(adventureId), SheetEdition.DND_5E_2024, new CharacterSheetData2024("Aria", 1, false));
        when(service.createSheet(any())).thenReturn(sheet);

        mockMvc.perform(post("/internal/v1/adventure-sessions/{sessionId}/character-sheets", adventureId)
                        .contentType("application/json")
                        .content("""
                                {"edition":"DND_5E_2024","characterName":"Aria","level":1,
                                 "derivedStatistics":"{\\"hitPointMaximum\\":12}","characterState":"{\\"note\\":\\"currentHitPoints is pending\\"}"}
                                """))
                .andExpect(status().isOk());

        var captor = org.mockito.ArgumentCaptor.forClass(com.dndmaster.character.application.CreateCharacterSheetCommand.class);
        verify(service).createSheet(captor.capture());
        org.junit.jupiter.api.Assertions.assertEquals(12,
                new com.fasterxml.jackson.databind.ObjectMapper().readTree(captor.getValue().data().characterState()).path("currentHitPoints").asInt());
    }

    @Test
    void keepsBuildAndMutableStateSeparateFromDerivedStatistics() throws Exception {
        UUID adventureId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        CharacterSheet sheet = new CharacterSheet(new CharacterSheetId(UUID.fromString("11111111-1111-1111-1111-111111111111")), new AdventureId(adventureId), SheetEdition.DND_5E_2024,
                new CharacterSheetData2024("Aria", 1, false, "Elf", "Wizard", "Sage", "dexterity=15", "{\"speed\":30}", "{\"subrace\":\"High Elf\"}", "{\"currentHitPoints\":6}"));
        when(service.createSheet(any())).thenReturn(sheet);

        mockMvc.perform(post("/internal/v1/adventure-sessions/{sessionId}/character-sheets", adventureId).contentType("application/json").content("""
                {"edition":"DND_5E_2024","characterName":"Aria","level":1,"characterBuild":"{\\"subrace\\":\\"High Elf\\"}","characterState":"{\\"currentHitPoints\\":6}"}
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.characterBuild").value("{\"subrace\":\"High Elf\"}"))
                .andExpect(jsonPath("$.characterState").value("{\"currentHitPoints\":6}"));
    }

    @Test
    void preservesExistingCharacterSheetById() throws Exception {
        UUID sheetId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID adventureId = UUID.fromString("44444444-4444-4444-4444-444444444444");
        CharacterSheet sheet = new CharacterSheet(
                new CharacterSheetId(sheetId),
                new AdventureId(adventureId),
                SheetEdition.DND_5E_2024,
                new CharacterSheetData2024("Borin", 4, false));
        when(service.openSheet(any(CharacterSheetId.class), eq(SheetEdition.DND_5E_2024))).thenReturn(sheet);
        when(service.manageCharacter(any(CharacterSheetId.class), any())).thenReturn(sheet);

        mockMvc.perform(get("/internal/v1/character-sheets/{sheetId}", sheetId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.characterSheetId").value(sheetId.toString()));

                mockMvc.perform(put("/internal/v1/character-sheets/{sheetId}", sheetId)
                        .header("X-Internal-Token", "test-internal-token")
                        .header("X-Session-ID", adventureId)
                        .header("X-Owner-Player-ID", UUID.randomUUID())
                        .header("Idempotency-Key", UUID.randomUUID())
                        .header("If-Match-Version", 0L)
                        .contentType("application/json")
                        .content("""
                                {
                                  "edition": "DND_5E_2024",
                                  "characterName": "Borin",
                                  "level": 4,
                                  "inspiration": false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.characterSheetId").value(sheetId.toString()));
    }

    @Test
    void rejects_unauthorized_deletion_request() throws Exception {
        org.mockito.Mockito.doThrow(new ApiRequestGuard.ApiContractException(401, "INVALID_SERVICE_TOKEN"))
                .when(requestGuard).internal(null);
        mockMvc.perform(post("/internal/v1/character-sheets/deletion-requests")
                        .contentType("application/json")
                        .content("{\"sessionId\":\"22222222-2222-2222-2222-222222222222\",\"characterSheetIds\":[]}"))
                .andExpect(status().isUnauthorized());
    }
}
