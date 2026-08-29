package com.dndmaster.adventure.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dndmaster.adventure.application.auth.PlayerSessionLookupPort;
import com.dndmaster.adventure.application.combat.AdventureCombatApplicationService;
import com.dndmaster.adventure.application.combat.CombatActorRole;
import com.dndmaster.adventure.application.combat.CombatMapPort;
import com.dndmaster.adventure.application.combat.CharacterCombatPort;
import com.dndmaster.adventure.application.combat.CombatResult;
import com.dndmaster.adventure.application.combat.RuntimeCombatRejectionException;
import com.dndmaster.adventure.application.guidance.RuleGuidanceApplicationService;
import com.dndmaster.adventure.application.runtime.RuntimeTurnApplicationService;
import com.dndmaster.adventure.application.runtime.GmTurnRepository;
import com.dndmaster.adventure.application.runtime.RuntimeTurnRepository;
import com.dndmaster.adventure.application.runtime.SessionEventRepository;
import com.dndmaster.adventure.application.runtime.GmTurnFailureRecorder;
import com.dndmaster.adventure.application.runtime.RuntimePlan;
import com.dndmaster.adventure.application.runtime.RuntimeTurn;
import com.dndmaster.adventure.application.runtime.RuntimeTurnResult;
import com.dndmaster.adventure.application.runtime.SubmitRuntimeTurnCommand;
import com.dndmaster.adventure.application.saved.AdventureRepository;
import com.dndmaster.adventure.application.saved.SavedAdventureApplicationService;
import com.dndmaster.adventure.application.scenario.AdventureScenarioApplicationService;
import com.dndmaster.adventure.application.storyplan.AdventureStoryPlanApplicationService;
import com.dndmaster.adventure.domain.scenario.AdventureScenario;
import com.dndmaster.adventure.domain.scenario.OwnerPlayerId;
import com.dndmaster.adventure.domain.scenario.ScenarioId;
import com.dndmaster.adventure.domain.scenario.ScenarioPreparationStatus;
import com.dndmaster.adventure.domain.scenario.ScenarioSource;
import com.dndmaster.adventure.domain.adventure.Adventure;
import com.dndmaster.adventure.domain.adventure.AdventureContext;
import com.dndmaster.adventure.domain.adventure.AdventurePartyMember;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.ControlMode;
import com.dndmaster.adventure.domain.adventure.CharacterSheetId;
import com.dndmaster.adventure.domain.adventure.RuleSetId;
import com.dndmaster.adventure.domain.adventure.SessionId;
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
    @Autowired AdventureController controller;

    @MockBean SavedAdventureApplicationService savedAdventureService;
    @MockBean RuntimeTurnApplicationService runtimeTurnService;
    @MockBean AdventureRepository adventureRepository;
    @MockBean GmTurnFailureRecorder gmTurnFailureRecorder;
    @MockBean GmTurnRepository gmTurnRepository;
    @MockBean RuntimeTurnRepository runtimeTurnRepository;
    @MockBean SessionEventRepository sessionEventRepository;
    @MockBean RuleGuidanceApplicationService guidanceService;
    @MockBean AdventureCombatApplicationService combatService;
    @MockBean CombatMapPort combatMapPort;
    @MockBean CharacterCombatPort characterCombatPort;
    @MockBean AdventureScenarioApplicationService scenarioService;
    @MockBean AdventureStoryPlanApplicationService storyPlanService;
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

    @Test
    void typedPlayerTurnIsNotMarkedAsGmOnly() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID adventureId = UUID.randomUUID();
        UUID turnId = UUID.randomUUID();
        UUID commandId = UUID.randomUUID();
        Adventure adventure = Adventure.create(
                new AdventureId(adventureId), new SessionId(UUID.randomUUID()),
                new com.dndmaster.adventure.domain.adventure.OwnerPlayerId(ownerId),
                new com.dndmaster.adventure.domain.adventure.ScenarioId(UUID.randomUUID()),
                new RuleSetId(UUID.randomUUID()), new CharacterSheetId(UUID.randomUUID()),
                new AdventureContext("scene", "", "", ""));
        when(playerResolver.playerId()).thenReturn(ownerId);
        when(adventureRepository.findById(new AdventureId(adventureId))).thenReturn(Optional.of(adventure));

        RuntimeTurnResult result = mock(RuntimeTurnResult.class);
        RuntimeTurn runtimeTurn = mock(RuntimeTurn.class);
        RuntimePlan plan = mock(RuntimePlan.class);
        AdventureContext context = new AdventureContext("scene", "", "", "");
        when(result.turn()).thenReturn(runtimeTurn);
        when(result.context()).thenReturn(context);
        when(result.version()).thenReturn(1L);
        when(runtimeTurn.turnId()).thenReturn(turnId);
        when(runtimeTurn.adventureId()).thenReturn(new AdventureId(adventureId));
        when(runtimeTurn.sessionId()).thenReturn(adventure.sessionId().value());
        when(runtimeTurn.scenarioPackageId()).thenReturn(UUID.randomUUID());
        when(runtimeTurn.bindingVersion()).thenReturn(1L);
        when(runtimeTurn.plan()).thenReturn(plan);
        when(runtimeTurn.citations()).thenReturn(java.util.List.of());
        when(runtimeTurn.warnings()).thenReturn(java.util.List.of());
        when(plan.narration()).thenReturn("The door opens.");
        when(plan.judgment()).thenReturn("No check required.");
        when(plan.provider()).thenReturn("test");
        when(plan.model()).thenReturn("test");
        when(plan.reasoning()).thenReturn("");
        when(runtimeTurnService.submitTurn(any())).thenReturn(result);

        mockMvc.perform(post("/api/v1/adventures/{adventureId}/turns", adventureId)
                        .header("Authorization", "Bearer " + ownerId)
                        .header("Idempotency-Key", commandId)
                        .header("If-Match-Version", 0)
                        .contentType("application/json")
                        .content("{\"turnId\":\"" + turnId + "\",\"input\":{\"type\":\"TEXT\",\"text\":\"open the door\"}}"))
                .andExpect(status().isAccepted());

        var captor = org.mockito.ArgumentCaptor.forClass(SubmitRuntimeTurnCommand.class);
        verify(runtimeTurnService).submitTurn(captor.capture());
        assertThat(captor.getValue().gmOnly()).isFalse();
        assertThat(captor.getValue().advancesState()).isTrue();
    }

    @Test
    void diceRollExposesJudgmentResolutionAndMutationApplicationState() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID adventureId = UUID.randomUUID();
        UUID ruleSetId = UUID.randomUUID();
        UUID sheetId = UUID.randomUUID();
        when(playerResolver.playerId()).thenReturn(ownerId);
        Adventure adventure = Adventure.create(
                new AdventureId(adventureId), new SessionId(UUID.randomUUID()),
                new com.dndmaster.adventure.domain.adventure.OwnerPlayerId(ownerId),
                new com.dndmaster.adventure.domain.adventure.ScenarioId(UUID.randomUUID()),
                new RuleSetId(ruleSetId), new CharacterSheetId(sheetId),
                new AdventureContext("scene", "", "", ""));
        when(adventureRepository.findById(new AdventureId(adventureId))).thenReturn(Optional.of(adventure));
        when(combatService.resolveCombatAction(any())).thenReturn(
                new CombatResult(UUID.randomUUID(), CombatActorRole.PLAYER, 20,
                        "critical hit (natural 20)", "RESOLVED", true));

        mockMvc.perform(post("/api/v1/adventures/{adventureId}/dice-rolls", adventureId)
                        .header("Authorization", "Bearer " + ownerId)
                        .contentType("application/json")
                        .content("{" +
                                "\"ruleSetId\":\"" + ruleSetId + "\",\"characterSheetId\":\"" + sheetId
                                + "\",\"ownerPlayerId\":\"" + ownerId
                                + "\",\"role\":\"PLAYER\",\"action\":\"attack\",\"expectedVersion\":0" + "}"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.judgment")
                        .value("critical hit (natural 20)"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.resolutionStatus")
                        .value("RESOLVED"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.outcomeApplied")
                        .value(true));
    }

    @Test
    void diceRollRejectsRuntimeCombatConditionAsUnprocessableEntity() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID adventureId = UUID.randomUUID();
        UUID ruleSetId = UUID.randomUUID();
        UUID sheetId = UUID.randomUUID();
        when(playerResolver.playerId()).thenReturn(ownerId);
        Adventure adventure = Adventure.create(
                new AdventureId(adventureId), new SessionId(UUID.randomUUID()),
                new com.dndmaster.adventure.domain.adventure.OwnerPlayerId(ownerId),
                new com.dndmaster.adventure.domain.adventure.ScenarioId(UUID.randomUUID()),
                new RuleSetId(ruleSetId), new CharacterSheetId(sheetId),
                new AdventureContext("scene", "", "", ""));
        when(adventureRepository.findById(new AdventureId(adventureId))).thenReturn(Optional.of(adventure));
        when(combatService.resolveCombatAction(any())).thenThrow(
                new RuntimeCombatRejectionException(RuntimeCombatRejectionException.ZERO_HIT_POINTS_MESSAGE));

        mockMvc.perform(post("/api/v1/adventures/{adventureId}/dice-rolls", adventureId)
                        .header("Authorization", "Bearer " + ownerId)
                        .contentType("application/json")
                        .content("{" +
                                "\"ruleSetId\":\"" + ruleSetId + "\",\"characterSheetId\":\"" + sheetId
                                + "\",\"ownerPlayerId\":\"" + ownerId
                                + "\",\"role\":\"PLAYER\",\"action\":\"attack\",\"expectedVersion\":0" + "}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.error")
                        .value(RuntimeCombatRejectionException.ERROR_CODE))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.message")
                        .value(RuntimeCombatRejectionException.ZERO_HIT_POINTS_MESSAGE));
    }

    @Test
    void diceRollRejectsNonPositiveDamageAmountAsBadRequest() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID adventureId = UUID.randomUUID();
        UUID ruleSetId = UUID.randomUUID();
        UUID sheetId = UUID.randomUUID();
        when(playerResolver.playerId()).thenReturn(ownerId);
        when(adventureRepository.findById(new AdventureId(adventureId))).thenReturn(Optional.of(Adventure.create(
                new AdventureId(adventureId), new SessionId(UUID.randomUUID()),
                new com.dndmaster.adventure.domain.adventure.OwnerPlayerId(ownerId),
                new com.dndmaster.adventure.domain.adventure.ScenarioId(UUID.randomUUID()), new RuleSetId(ruleSetId),
                new CharacterSheetId(sheetId), new AdventureContext("scene", "", "", ""))));

        mockMvc.perform(post("/api/v1/adventures/{adventureId}/dice-rolls", adventureId)
                        .header("Authorization", "Bearer " + ownerId)
                        .contentType("application/json")
                        .content("{\"ruleSetId\":\"" + ruleSetId + "\",\"characterSheetId\":\"" + sheetId
                                + "\",\"ownerPlayerId\":\"" + ownerId
                                + "\",\"role\":\"PLAYER\",\"action\":\"attack\",\"damageAmount\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.error")
                        .value("INVALID_DAMAGE_AMOUNT"));
        org.mockito.Mockito.verifyNoInteractions(combatService);
    }

    @Test
    void diceRollRejectsDamageWithoutExplicitTargetAsBadRequest() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID adventureId = UUID.randomUUID();
        UUID ruleSetId = UUID.randomUUID();
        UUID sheetId = UUID.randomUUID();
        when(playerResolver.playerId()).thenReturn(ownerId);
        when(adventureRepository.findById(new AdventureId(adventureId))).thenReturn(Optional.of(Adventure.create(
                new AdventureId(adventureId), new SessionId(UUID.randomUUID()),
                new com.dndmaster.adventure.domain.adventure.OwnerPlayerId(ownerId),
                new com.dndmaster.adventure.domain.adventure.ScenarioId(UUID.randomUUID()), new RuleSetId(ruleSetId),
                new CharacterSheetId(sheetId), new AdventureContext("scene", "", "", ""))));

        mockMvc.perform(post("/api/v1/adventures/{adventureId}/dice-rolls", adventureId)
                        .header("Authorization", "Bearer " + ownerId)
                        .contentType("application/json")
                        .content("{\"ruleSetId\":\"" + ruleSetId + "\",\"characterSheetId\":\"" + sheetId
                                + "\",\"ownerPlayerId\":\"" + ownerId
                                + "\",\"role\":\"PLAYER\",\"action\":\"attack\",\"damageAmount\":5}"))
                .andExpect(status().isBadRequest())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.error")
                        .value("INVALID_COMBAT_TARGET"));
        org.mockito.Mockito.verifyNoInteractions(combatService);
    }

    @Test
    void diceRollRejectsForgedGmRoleOnPublicEndpoint() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID adventureId = UUID.randomUUID();
        UUID ruleSetId = UUID.randomUUID();
        UUID sheetId = UUID.randomUUID();
        when(playerResolver.playerId()).thenReturn(ownerId);

        mockMvc.perform(post("/api/v1/adventures/{adventureId}/dice-rolls", adventureId)
                        .header("Authorization", "Bearer " + ownerId)
                        .contentType("application/json")
                        .content("{\"ruleSetId\":\"" + ruleSetId + "\",\"characterSheetId\":\"" + sheetId
                                + "\",\"ownerPlayerId\":\"" + UUID.randomUUID()
                                + "\",\"role\":\"GM\",\"action\":\"attack\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.error")
                        .value("INVALID_COMBAT_ROLE"));
        org.mockito.Mockito.verifyNoInteractions(combatService, adventureRepository);
    }

    @Test
    void diceRollUsesAuthenticatedOwnerInsteadOfRequestOwner() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID forgedOwnerId = UUID.randomUUID();
        UUID adventureId = UUID.randomUUID();
        UUID ruleSetId = UUID.randomUUID();
        UUID sheetId = UUID.randomUUID();
        when(playerResolver.playerId()).thenReturn(ownerId);
        when(adventureRepository.findById(new AdventureId(adventureId))).thenReturn(Optional.of(Adventure.create(
                new AdventureId(adventureId), new SessionId(UUID.randomUUID()),
                new com.dndmaster.adventure.domain.adventure.OwnerPlayerId(ownerId),
                new com.dndmaster.adventure.domain.adventure.ScenarioId(UUID.randomUUID()), new RuleSetId(ruleSetId),
                new CharacterSheetId(sheetId), new AdventureContext("scene", "", "", ""))));
        when(combatService.resolveCombatAction(any())).thenReturn(
                new CombatResult(UUID.randomUUID(), CombatActorRole.PLAYER, 10, "pending", "PENDING_RULE_INPUT", false));

        mockMvc.perform(post("/api/v1/adventures/{adventureId}/dice-rolls", adventureId)
                        .header("Authorization", "Bearer " + ownerId)
                        .contentType("application/json")
                        .content("{\"ruleSetId\":\"" + ruleSetId + "\",\"characterSheetId\":\"" + sheetId
                                + "\",\"ownerPlayerId\":\"" + forgedOwnerId
                                + "\",\"role\":\"PLAYER\",\"action\":\"attack\"}"))
                .andExpect(status().isOk());
        var captor = org.mockito.ArgumentCaptor.forClass(com.dndmaster.adventure.application.combat.CombatActionCommand.class);
        verify(combatService).resolveCombatAction(captor.capture());
        assertThat(captor.getValue().ownerPlayerId()).isEqualTo(ownerId);
    }

    @Test
    void diceRollRejectsCharacterSheetOutsideAdventureParty() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID adventureId = UUID.randomUUID();
        UUID ruleSetId = UUID.randomUUID();
        UUID partySheetId = UUID.randomUUID();
        UUID foreignSheetId = UUID.randomUUID();
        when(playerResolver.playerId()).thenReturn(ownerId);
        when(adventureRepository.findById(new AdventureId(adventureId))).thenReturn(Optional.of(Adventure.create(
                new AdventureId(adventureId), new SessionId(UUID.randomUUID()),
                new com.dndmaster.adventure.domain.adventure.OwnerPlayerId(ownerId),
                new com.dndmaster.adventure.domain.adventure.ScenarioId(UUID.randomUUID()), new RuleSetId(ruleSetId),
                new CharacterSheetId(partySheetId), new AdventureContext("scene", "", "", ""))));

        mockMvc.perform(post("/api/v1/adventures/{adventureId}/dice-rolls", adventureId)
                        .header("Authorization", "Bearer " + ownerId)
                        .contentType("application/json")
                        .content("{\"ruleSetId\":\"" + ruleSetId + "\",\"characterSheetId\":\"" + foreignSheetId
                                + "\",\"ownerPlayerId\":\"" + ownerId
                                + "\",\"role\":\"PLAYER\",\"action\":\"attack\"}"))
                .andExpect(status().isForbidden())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.error")
                        .value("CHARACTER_NOT_IN_ADVENTURE"));
        org.mockito.Mockito.verifyNoInteractions(combatService);
    }

    @Test
    void diceRollRejectsRuleSetOutsideAdventure() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID adventureId = UUID.randomUUID();
        UUID adventureRuleSetId = UUID.randomUUID();
        UUID requestedRuleSetId = UUID.randomUUID();
        UUID sheetId = UUID.randomUUID();
        when(playerResolver.playerId()).thenReturn(ownerId);
        when(adventureRepository.findById(new AdventureId(adventureId))).thenReturn(Optional.of(Adventure.create(
                new AdventureId(adventureId), new SessionId(UUID.randomUUID()),
                new com.dndmaster.adventure.domain.adventure.OwnerPlayerId(ownerId),
                new com.dndmaster.adventure.domain.adventure.ScenarioId(UUID.randomUUID()), new RuleSetId(adventureRuleSetId),
                new CharacterSheetId(sheetId), new AdventureContext("scene", "", "", ""))));

        mockMvc.perform(post("/api/v1/adventures/{adventureId}/dice-rolls", adventureId)
                        .header("Authorization", "Bearer " + ownerId)
                        .contentType("application/json")
                        .content("{\"ruleSetId\":\"" + requestedRuleSetId + "\",\"characterSheetId\":\"" + sheetId
                                + "\",\"ownerPlayerId\":\"" + ownerId
                                + "\",\"role\":\"PLAYER\",\"action\":\"attack\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.error")
                        .value("INVALID_RULE_SET"));
        org.mockito.Mockito.verifyNoInteractions(combatService);
    }

    @Test
    void diceRollRejectsAdventureOwnedByAnotherPlayer() throws Exception {
        UUID authenticatedOwner = UUID.randomUUID();
        UUID adventureOwner = UUID.randomUUID();
        UUID adventureId = UUID.randomUUID();
        UUID ruleSetId = UUID.randomUUID();
        UUID sheetId = UUID.randomUUID();
        when(playerResolver.playerId()).thenReturn(authenticatedOwner);
        when(adventureRepository.findById(new AdventureId(adventureId))).thenReturn(Optional.of(Adventure.create(
                new AdventureId(adventureId), new SessionId(UUID.randomUUID()),
                new com.dndmaster.adventure.domain.adventure.OwnerPlayerId(adventureOwner),
                new com.dndmaster.adventure.domain.adventure.ScenarioId(UUID.randomUUID()), new RuleSetId(ruleSetId),
                new CharacterSheetId(sheetId), new AdventureContext("scene", "", "", ""))));

        mockMvc.perform(post("/api/v1/adventures/{adventureId}/dice-rolls", adventureId)
                        .header("Authorization", "Bearer " + authenticatedOwner)
                        .contentType("application/json")
                        .content("{\"ruleSetId\":\"" + ruleSetId + "\",\"characterSheetId\":\"" + sheetId
                                + "\",\"ownerPlayerId\":\"" + authenticatedOwner
                                + "\",\"role\":\"PLAYER\",\"action\":\"attack\"}"))
                .andExpect(status().isForbidden())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.error")
                        .value("OWNERSHIP_DENIED"));
        org.mockito.Mockito.verifyNoInteractions(combatService);
    }

    @Test
    void saveUsesAuthenticatedOwnerInsteadOfRequestPlayerId() throws Exception {
        UUID authenticatedOwner = UUID.randomUUID();
        UUID forgedOwner = UUID.randomUUID();
        UUID adventureId = UUID.randomUUID();
        when(playerResolver.playerId()).thenReturn(authenticatedOwner);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                        "/api/v1/adventures/{adventureId}/save", adventureId)
                        .header("Authorization", "Bearer " + authenticatedOwner)
                        .contentType("application/json")
                        .content("{\"playerId\":\"" + forgedOwner + "\",\"expectedVersion\":4,\"currentScene\":\"scene\"}"))
                .andExpect(status().isOk());
        var captor = org.mockito.ArgumentCaptor.forClass(com.dndmaster.adventure.domain.adventure.OwnerPlayerId.class);
        verify(savedAdventureService).preserveProgress(any(), captor.capture(), org.mockito.ArgumentMatchers.eq(4L), any(), any());
        assertThat(captor.getValue().value()).isEqualTo(authenticatedOwner);
    }

    @Test
    void deleteUsesAuthenticatedOwnerInsteadOfRequestPlayerId() throws Exception {
        UUID authenticatedOwner = UUID.randomUUID();
        UUID forgedOwner = UUID.randomUUID();
        UUID adventureId = UUID.randomUUID();
        when(playerResolver.playerId()).thenReturn(authenticatedOwner);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                        "/api/v1/adventures/{adventureId}", adventureId)
                        .header("Authorization", "Bearer " + authenticatedOwner)
                        .contentType("application/json")
                        .content("{\"playerId\":\"" + forgedOwner + "\",\"expectedVersion\":4}"))
                .andExpect(status().isNoContent());
        var captor = org.mockito.ArgumentCaptor.forClass(com.dndmaster.adventure.domain.adventure.OwnerPlayerId.class);
        verify(savedAdventureService).deleteAdventure(any(), captor.capture(), org.mockito.ArgumentMatchers.eq(4L));
        assertThat(captor.getValue().value()).isEqualTo(authenticatedOwner);
    }

    @Test
    void ruleInquiryRejectsRuleSetOutsideAdventure() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID adventureId = UUID.randomUUID();
        UUID adventureRuleSetId = UUID.randomUUID();
        UUID requestedRuleSetId = UUID.randomUUID();
        when(playerResolver.playerId()).thenReturn(ownerId);
        when(adventureRepository.findById(new AdventureId(adventureId))).thenReturn(Optional.of(Adventure.create(
                new AdventureId(adventureId), new SessionId(UUID.randomUUID()),
                new com.dndmaster.adventure.domain.adventure.OwnerPlayerId(ownerId),
                new com.dndmaster.adventure.domain.adventure.ScenarioId(UUID.randomUUID()), new RuleSetId(adventureRuleSetId),
                new CharacterSheetId(UUID.randomUUID()), new AdventureContext("scene", "", "", ""))));

        mockMvc.perform(post("/api/v1/adventures/{adventureId}/rule-inquiries", adventureId)
                        .header("Authorization", "Bearer " + ownerId)
                        .contentType("application/json")
                        .content("{\"inquiryId\":\"" + UUID.randomUUID() + "\",\"ruleSetId\":\""
                                + requestedRuleSetId + "\",\"playerId\":\"" + UUID.randomUUID()
                                + "\",\"situation\":\"attack\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.error")
                        .value("INVALID_RULE_SET"));
        org.mockito.Mockito.verifyNoInteractions(guidanceService);
    }

    @Test
    void mapMoveResolvesCharacterSheetFromNonFirstPartyToken() {
        UUID ownerId = UUID.randomUUID();
        UUID adventureId = UUID.randomUUID();
        UUID firstSheet = UUID.randomUUID();
        UUID secondSheet = UUID.randomUUID();
        UUID mapId = UUID.randomUUID();
        UUID tokenId = UUID.nameUUIDFromBytes(("player-" + secondSheet).getBytes(StandardCharsets.UTF_8));
        Adventure adventure = Adventure.create(new AdventureId(adventureId), new SessionId(UUID.randomUUID()),
                new com.dndmaster.adventure.domain.adventure.OwnerPlayerId(ownerId),
                new com.dndmaster.adventure.domain.adventure.ScenarioId(UUID.randomUUID()), new RuleSetId(UUID.randomUUID()),
                java.util.List.of(
                        new AdventurePartyMember(new CharacterSheetId(firstSheet), ControlMode.DIRECT, true, true, true, true, true, true),
                        new AdventurePartyMember(new CharacterSheetId(secondSheet), ControlMode.DIRECT, true, true, true, true, true, true)),
                new AdventureContext("scene", "", "", ""));
        var input = new com.dndmaster.adventure.domain.runtime.GmInput.MapActionInput(mapId, 3,
                "{\"mapId\":\"" + mapId + "\",\"mapVersion\":3,\"tokenId\":\"" + tokenId
                        + "\",\"action\":\"MOVE\",\"path\":[{\"x\":0,\"y\":0},{\"x\":1,\"y\":0}]}" );

        org.springframework.test.util.ReflectionTestUtils.invokeMethod(controller, "applyMapAction", adventure, ownerId,
                UUID.randomUUID(), input);

        var captor = org.mockito.ArgumentCaptor.forClass(com.dndmaster.adventure.application.combat.CombatActionCommand.class);
        verify(combatMapPort).validateAndMove(captor.capture());
        assertThat(captor.getValue().characterSheetId()).isEqualTo(new CharacterSheetId(secondSheet));
        assertThat(captor.getValue().tokenId()).isEqualTo(tokenId);
    }

    @Test
    void mapMoveRejectsUnknownTokenWithoutForwarding() {
        UUID ownerId = UUID.randomUUID();
        UUID mapId = UUID.randomUUID();
        Adventure adventure = Adventure.create(new AdventureId(UUID.randomUUID()), new SessionId(UUID.randomUUID()),
                new com.dndmaster.adventure.domain.adventure.OwnerPlayerId(ownerId),
                new com.dndmaster.adventure.domain.adventure.ScenarioId(UUID.randomUUID()), new RuleSetId(UUID.randomUUID()),
                new CharacterSheetId(UUID.randomUUID()), new AdventureContext("scene", "", "", ""));
        UUID unknownToken = UUID.randomUUID();
        var input = new com.dndmaster.adventure.domain.runtime.GmInput.MapActionInput(mapId, 3,
                "{\"mapId\":\"" + mapId + "\",\"mapVersion\":3,\"tokenId\":\"" + unknownToken
                        + "\",\"action\":\"MOVE\",\"path\":[]}" );

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                org.springframework.test.util.ReflectionTestUtils.invokeMethod(controller, "applyMapAction", adventure, ownerId,
                        UUID.randomUUID(), input))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("map action token does not belong to the party");
        org.mockito.Mockito.verifyNoInteractions(combatMapPort);
    }

    @Test
    void mapMoveRejectsZeroHitPointCharacterWithoutForwarding() {
        UUID ownerId = UUID.randomUUID();
        UUID mapId = UUID.randomUUID();
        UUID sheetId = UUID.randomUUID();
        Adventure adventure = Adventure.create(new AdventureId(UUID.randomUUID()), new SessionId(UUID.randomUUID()),
                new com.dndmaster.adventure.domain.adventure.OwnerPlayerId(ownerId),
                new com.dndmaster.adventure.domain.adventure.ScenarioId(UUID.randomUUID()), new RuleSetId(UUID.randomUUID()),
                java.util.List.of(new AdventurePartyMember(new CharacterSheetId(sheetId), ControlMode.DIRECT, true, true, true, true, true, true)),
                new AdventureContext("scene", "", "", ""));
        UUID tokenId = UUID.nameUUIDFromBytes(("player-" + sheetId).getBytes(StandardCharsets.UTF_8));
        var input = new com.dndmaster.adventure.domain.runtime.GmInput.MapActionInput(mapId, 3,
                "{\"mapId\":\"" + mapId + "\",\"mapVersion\":3,\"tokenId\":\"" + tokenId
                        + "\",\"action\":\"MOVE\",\"path\":[{\"x\":0,\"y\":0},{\"x\":1,\"y\":0}]}" );
        org.mockito.Mockito.doThrow(new RuntimeCombatRejectionException(RuntimeCombatRejectionException.ZERO_HIT_POINTS_MESSAGE))
                .when(characterCombatPort).requireUsableCharacter(any());

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                org.springframework.test.util.ReflectionTestUtils.invokeMethod(controller, "applyMapAction", adventure, ownerId,
                        UUID.randomUUID(), input))
                .isInstanceOf(RuntimeCombatRejectionException.class)
                .hasMessage(RuntimeCombatRejectionException.ZERO_HIT_POINTS_MESSAGE);
        verify(characterCombatPort).requireUsableCharacter(any());
        org.mockito.Mockito.verifyNoInteractions(combatMapPort);
    }

    @Test
    void unsupportedMapActionIsRejectedWithoutForwarding() {
        UUID ownerId = UUID.randomUUID();
        UUID mapId = UUID.randomUUID();
        Adventure adventure = Adventure.create(new AdventureId(UUID.randomUUID()), new SessionId(UUID.randomUUID()),
                new com.dndmaster.adventure.domain.adventure.OwnerPlayerId(ownerId),
                new com.dndmaster.adventure.domain.adventure.ScenarioId(UUID.randomUUID()), new RuleSetId(UUID.randomUUID()),
                new CharacterSheetId(UUID.randomUUID()), new AdventureContext("scene", "", "", ""));
        var input = new com.dndmaster.adventure.domain.runtime.GmInput.MapActionInput(mapId, 3,
                "{\"mapId\":\"" + mapId + "\",\"mapVersion\":3,\"action\":\"ATTACK\"}" );

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                org.springframework.test.util.ReflectionTestUtils.invokeMethod(controller, "applyMapAction", adventure, ownerId,
                        UUID.randomUUID(), input))
                .isInstanceOf(ApiRequestGuard.ApiContractException.class)
                .hasMessage("UNSUPPORTED_MAP_ACTION");
        org.mockito.Mockito.verifyNoInteractions(characterCombatPort, combatMapPort);
    }

    @Test
    void mapMoveRejectsMissingPathWithoutForwarding() {
        UUID ownerId = UUID.randomUUID();
        UUID mapId = UUID.randomUUID();
        UUID sheetId = UUID.randomUUID();
        Adventure adventure = Adventure.create(new AdventureId(UUID.randomUUID()), new SessionId(UUID.randomUUID()),
                new com.dndmaster.adventure.domain.adventure.OwnerPlayerId(ownerId),
                new com.dndmaster.adventure.domain.adventure.ScenarioId(UUID.randomUUID()), new RuleSetId(UUID.randomUUID()),
                java.util.List.of(new AdventurePartyMember(new CharacterSheetId(sheetId), ControlMode.DIRECT, true, true, true, true, true, true)),
                new AdventureContext("scene", "", "", ""));
        UUID tokenId = UUID.nameUUIDFromBytes(("player-" + sheetId).getBytes(StandardCharsets.UTF_8));
        var input = new com.dndmaster.adventure.domain.runtime.GmInput.MapActionInput(mapId, 3,
                "{\"mapId\":\"" + mapId + "\",\"mapVersion\":3,\"tokenId\":\"" + tokenId
                        + "\",\"action\":\"MOVE\",\"path\":[{\"x\":0,\"y\":0}]}" );

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                org.springframework.test.util.ReflectionTestUtils.invokeMethod(controller, "applyMapAction", adventure, ownerId,
                        UUID.randomUUID(), input))
                .isInstanceOf(ApiRequestGuard.ApiContractException.class)
                .hasMessage("INVALID_MAP_MOVE_PATH");
        org.mockito.Mockito.verifyNoInteractions(characterCombatPort, combatMapPort);
    }
}
