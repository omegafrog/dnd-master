package com.dndmaster.adventure.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dndmaster.adventure.application.combat.CombatActionApplicationService;
import com.dndmaster.adventure.application.combat.CombatActionCommand;
import com.dndmaster.adventure.application.combat.CombatActionResponse;
import com.dndmaster.adventure.application.combat.CombatEncounterRepository;
import com.dndmaster.adventure.application.combat.CombatMapViewPort;
import com.dndmaster.adventure.application.scenario.compilation.ScenarioPackageRepository;
import com.dndmaster.adventure.application.saved.AdventureRepository;
import com.dndmaster.adventure.application.session.AdventureAiRequestApplicationService;
import com.dndmaster.adventure.application.session.AdventureSessionRepository;
import com.dndmaster.adventure.domain.adventure.Adventure;
import com.dndmaster.adventure.domain.adventure.AdventureContext;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.CharacterSheetId;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import com.dndmaster.adventure.domain.adventure.RuleSetId;
import com.dndmaster.adventure.domain.adventure.ScenarioId;
import com.dndmaster.adventure.domain.adventure.SessionId;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class CombatControllerMovementPendingTest {
    @Test
    void action_and_roll_routes_return_the_saved_pending_ruling_contract() throws Exception {
        UUID adventureId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID characterId = UUID.randomUUID();
        UUID encounterId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        UUID turnId = UUID.randomUUID();
        UUID hostileTokenId = UUID.randomUUID();
        SessionId sessionId = SessionId.generate();
        Adventure adventure = Adventure.create(new AdventureId(adventureId), sessionId, new OwnerPlayerId(ownerId),
                new ScenarioId(UUID.randomUUID()), new RuleSetId(UUID.randomUUID()), new CharacterSheetId(characterId),
                new AdventureContext("cellar", null, null, null));
        AdventureRepository adventures = mock(AdventureRepository.class);
        when(adventures.findById(new AdventureId(adventureId))).thenReturn(Optional.of(adventure));
        AuthenticatedPlayerResolver players = mock(AuthenticatedPlayerResolver.class);
        when(players.playerId()).thenReturn(ownerId);
        CombatEncounterRepository encounters = mock(CombatEncounterRepository.class);
        when(encounters.findActive(adventureId)).thenReturn(Optional.empty());

        var continuation = new com.dndmaster.adventure.application.runtime.CombatContinuationCommand(
                UUID.randomUUID(), turnId, operationId, hostileTokenId, "HOSTILE_OBSERVED",
                com.dndmaster.adventure.application.combat.MovementFollowUpCommand.Kind.COMBAT,
                "판정 결과를 기다립니다.", "플레이어 굴림을 제출해 주세요.",
                new com.dndmaster.adventure.application.runtime.RuntimeCheckProposal(true, "길을 살핍니다.", "지각",
                        new UUID(0, 1), com.dndmaster.adventure.application.runtime.RuntimeCheckProposal.RollMethod.PLAYER,
                        "1d20", 2, 12, List.of("rulebook:check"), "발견합니다.", "지나칩니다."), true, false);
        CombatActionResponse pending = new CombatActionResponse(encounterId, operationId, 7,
                "GM_INTERVENTION_PENDING", null, null, List.of(), null, continuation);
        CombatActionApplicationService actions = mock(CombatActionApplicationService.class);
        when(actions.submit(any(CombatActionCommand.class))).thenReturn(pending);
        when(actions.submitMovementPlayerRoll(eq(adventureId), eq(operationId), eq(turnId), eq(14), eq(7L)))
                .thenReturn(pending);

        AdventureSessionRepository sessions = mock(AdventureSessionRepository.class);
        when(sessions.tryAcquireAiRequest(any(), any(), any())).thenReturn(true);
        when(sessions.releaseAiRequest(any(), any(), any())).thenReturn(true);
        var controller = new CombatController(encounters, players, adventures,
                mock(com.dndmaster.adventure.application.combat.CombatEventRepository.class), actions,
                mock(com.dndmaster.adventure.application.combat.CombatReactionApplicationService.class),
                mock(com.dndmaster.adventure.application.combat.CombatWorkItemRepository.class),
                mock(com.dndmaster.adventure.application.combat.CombatWorkItemScheduler.class),
                mock(com.dndmaster.adventure.application.combat.CharacterCombatPort.class),
                new AdventureAiRequestApplicationService(sessions), mock(ScenarioPackageRepository.class),
                mock(CombatMapViewPort.class));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();
        ObjectMapper mapper = new ObjectMapper();

        var actionResult = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/adventures/{adventureId}/combat/actions", adventureId)
                        .header("Idempotency-Key", operationId)
                        .header("If-Match-Version", "7")
                        .contentType("application/json")
                        .content("{\"characterSheetId\":\"" + characterId
                                + "\",\"action\":\"MOVE\",\"movementPath\":[{\"x\":0,\"y\":0},{\"x\":1,\"y\":0}],\"movementDistance\":5,\"mapVersion\":3}"))
                .andReturn();
        assertEquals(202, actionResult.getResponse().getStatus());
        var response = mapper.readTree(actionResult.getResponse().getContentAsString());
        assertEquals("GM_INTERVENTION_PENDING", response.path("status").asText());
        assertEquals(operationId.toString(), response.path("operationId").asText());
        assertEquals(turnId.toString(), response.path("movementAdjudication").path("turnId").asText());
        assertEquals(true, response.path("movementAdjudication").path("waitingForPlayerInput").asBoolean());

        var rollResult = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/adventures/{adventureId}/combat/actions/{operationId}/roll/{turnId}",
                                adventureId, operationId, turnId)
                        .contentType("application/json")
                        .content("{\"result\":14,\"expectedVersion\":7}"))
                .andReturn();
        assertEquals(202, rollResult.getResponse().getStatus());
        assertEquals("GM_INTERVENTION_PENDING", mapper.readTree(rollResult.getResponse().getContentAsString())
                .path("status").asText());
        verify(actions).submitMovementPlayerRoll(adventureId, operationId, turnId, 14, 7L);
    }
}
