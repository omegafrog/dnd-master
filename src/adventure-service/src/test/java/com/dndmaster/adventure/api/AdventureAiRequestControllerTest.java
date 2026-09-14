package com.dndmaster.adventure.api;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dndmaster.adventure.application.combat.AdventureCombatApplicationService;
import com.dndmaster.adventure.application.combat.CombatActionApplicationService;
import com.dndmaster.adventure.application.runtime.GmTurnRepository;
import com.dndmaster.adventure.application.runtime.RuntimeTurnApplicationService;
import com.dndmaster.adventure.application.runtime.RuntimeTurnRepository;
import com.dndmaster.adventure.application.saved.AdventureRepository;
import com.dndmaster.adventure.application.session.AdventureAiRequestApplicationService;
import com.dndmaster.adventure.application.session.AdventureAiRequestInProgressException;
import com.dndmaster.adventure.domain.adventure.Adventure;
import com.dndmaster.adventure.domain.adventure.AdventureContext;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.CharacterSheetId;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import com.dndmaster.adventure.domain.adventure.RuleSetId;
import com.dndmaster.adventure.domain.adventure.ScenarioId;
import com.dndmaster.adventure.domain.adventure.SessionId;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class AdventureAiRequestControllerTest {
    @Test
    void competing_chat_or_map_action_is_rejected_before_any_turn_is_saved() {
        Fixture fixture = fixture();
        UUID requestId = UUID.randomUUID();
        doThrow(new AdventureAiRequestInProgressException()).when(fixture.aiRequests())
                .begin(fixture.adventure().sessionId(), fixture.adventure().ownerPlayerId(), requestId);

        assertThrows(AdventureAiRequestInProgressException.class, () -> fixture.adventureController().submitTypedTurn(
                fixture.adventure().id().value(), requestId, fixture.adventure().version(),
                new AdventureController.GmTurnRequest(UUID.randomUUID(),
                        new AdventureController.GmInputRequest("TEXT", "문을 살펴본다", null, null, null, null))));

        verify(fixture.gmTurns(), never()).save(any(), any());
        verify(fixture.runtimeTurns(), never()).submitTurn(any());
    }

    @Test
    void competing_combat_action_is_rejected_before_the_action_service_can_persist_it() {
        Fixture fixture = fixture();
        UUID requestId = UUID.randomUUID();
        doThrow(new AdventureAiRequestInProgressException()).when(fixture.aiRequests())
                .begin(fixture.adventure().sessionId(), fixture.adventure().ownerPlayerId(), requestId);

        assertThrows(AdventureAiRequestInProgressException.class, () -> fixture.combatController().action(
                fixture.adventure().id().value(), requestId.toString(), fixture.adventure().version(),
                new CombatController.CombatActionRequest(UUID.randomUUID(), "공격", null, null, null, null)));

        verify(fixture.combatActions(), never()).submit(any());
    }

    @SuppressWarnings("unchecked")
    private static Fixture fixture() {
        OwnerPlayerId owner = new OwnerPlayerId(UUID.randomUUID());
        Adventure adventure = Adventure.create(AdventureId.generate(), SessionId.generate(), owner,
                new ScenarioId(UUID.randomUUID()), new RuleSetId(UUID.randomUUID()),
                new CharacterSheetId(UUID.randomUUID()), new AdventureContext("장면", null, null, null));
        AdventureRepository adventures = mock(AdventureRepository.class);
        when(adventures.findById(adventure.id())).thenReturn(Optional.of(adventure));
        AuthenticatedPlayerResolver playerResolver = mock(AuthenticatedPlayerResolver.class);
        when(playerResolver.playerId()).thenReturn(owner.value());
        AdventureAiRequestApplicationService aiRequests = mock(AdventureAiRequestApplicationService.class);
        GmTurnRepository gmTurns = mock(GmTurnRepository.class);
        when(gmTurns.findByCommandId(any())).thenReturn(Optional.empty());
        RuntimeTurnApplicationService runtimeTurns = mock(RuntimeTurnApplicationService.class);
        CombatActionApplicationService combatActions = mock(CombatActionApplicationService.class);

        AdventureController adventureController = new AdventureController(
                mock(com.dndmaster.adventure.application.saved.SavedAdventureApplicationService.class), runtimeTurns,
                adventures, mock(com.dndmaster.adventure.application.runtime.GmTurnFailureRecorder.class), gmTurns,
                mock(RuntimeTurnRepository.class), mock(com.dndmaster.adventure.application.runtime.SessionEventRepository.class),
                mock(com.dndmaster.adventure.application.guidance.RuleGuidanceApplicationService.class),
                mock(AdventureCombatApplicationService.class), combatActions,
                mock(com.dndmaster.adventure.application.scenario.AdventureScenarioApplicationService.class),
                playerResolver, mock(ObjectProvider.class), mock(ObjectProvider.class), new ObjectMapper(),
                mock(ObjectProvider.class), mock(ObjectProvider.class),
                mock(com.dndmaster.adventure.application.scenario.compilation.ScenarioPackageRepository.class),
                mock(com.dndmaster.adventure.application.combat.CombatLifecycleApplicationService.class), aiRequests);
        CombatController combatController = new CombatController(
                mock(com.dndmaster.adventure.application.combat.CombatEncounterRepository.class), playerResolver,
                adventures, mock(com.dndmaster.adventure.application.combat.CombatEventRepository.class), combatActions,
                mock(com.dndmaster.adventure.application.combat.CombatReactionApplicationService.class),
                mock(com.dndmaster.adventure.application.combat.CombatWorkItemRepository.class),
                mock(com.dndmaster.adventure.application.combat.CombatWorkItemScheduler.class),
                mock(com.dndmaster.adventure.application.combat.CharacterCombatPort.class), aiRequests);
        return new Fixture(adventure, adventureController, combatController, aiRequests, gmTurns, runtimeTurns,
                combatActions);
    }

    private record Fixture(Adventure adventure, AdventureController adventureController,
            CombatController combatController, AdventureAiRequestApplicationService aiRequests,
            GmTurnRepository gmTurns, RuntimeTurnApplicationService runtimeTurns,
            CombatActionApplicationService combatActions) { }
}
