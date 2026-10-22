package com.dndmaster.adventure.application.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.dndmaster.adventure.application.knowledge.SessionKnowledgeSetRepository;
import com.dndmaster.adventure.application.saved.AdventureRepository;
import com.dndmaster.adventure.application.scenario.compilation.ScenarioPackageRepository;
import com.dndmaster.adventure.domain.adventure.Adventure;
import com.dndmaster.adventure.domain.adventure.AdventureContext;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.CharacterSheetId;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import com.dndmaster.adventure.domain.adventure.RuleSetId;
import com.dndmaster.adventure.domain.adventure.ScenarioId;
import com.dndmaster.adventure.domain.adventure.SessionId;
import com.dndmaster.adventure.domain.runtime.CompletionProposal;
import com.dndmaster.adventure.domain.runtime.CurrentSituation;
import com.dndmaster.adventure.domain.runtime.DisclosureState;
import com.dndmaster.adventure.domain.runtime.GameState;
import com.dndmaster.adventure.domain.runtime.GameStateDelta;
import com.dndmaster.adventure.domain.runtime.PendingRuntimeState;
import com.dndmaster.adventure.domain.scenario.ScenarioPackage;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class RuntimeTurnPlayerRollCommitGateTest {
    @Test
    void runs_map_commit_gate_when_player_roll_completes_a_map_situation_transition() {
        UUID packageId = UUID.randomUUID();
        UUID mapDefinitionId = UUID.randomUUID();
        AdventureId adventureId = AdventureId.generate();
        OwnerPlayerId owner = new OwnerPlayerId(UUID.randomUUID());
        SessionId sessionId = SessionId.generate();
        CharacterSheetId characterSheetId = new CharacterSheetId(UUID.randomUUID());
        Adventure adventure = Adventure.beginScenarioRuntime(adventureId, sessionId, owner,
                new ScenarioId(UUID.randomUUID()), new RuleSetId(UUID.randomUUID()), packageId, 1,
                List.of(new com.dndmaster.adventure.domain.adventure.AdventurePartyMember(characterSheetId,
                        com.dndmaster.adventure.domain.adventure.ControlMode.DIRECT, true, true, true, true, true, true)),
                new AdventureContext("양조장", "저장고를 조사한다", null, null));
        CurrentSituation starting = new CurrentSituation(UUID.randomUUID(), 1, "양조장", "저장고를 조사한다",
                "쥐의 위협", "저장고를 살핀다");
        adventure.initializeScenarioRuntime(owner, GameState.empty(), DisclosureState.empty(), starting, List.of(),
                adventure.currentContext());
        CurrentSituation destination = new CurrentSituation(UUID.randomUUID(), 1, "맥주 저장고 입구",
                "저장고를 조사한다", "어둠 속의 쥐", "쥐의 근원을 찾는다", null, null,
                "맥주 저장고 입구", "양조장 지하 맥주 저장고", mapDefinitionId);

        RuntimePlan plan = new RuntimePlan("맥주 저장고 입구", null, "쥐 움직임을 살핀다", "쥐의 움직임을 알아차립니다.",
                null, List.of(), List.of(), "codex-cli", "gpt", "", false, "", null, null,
                1, List.of(), null, List.of(), false, true,
                new RuntimeCheckProposal(true, "움직임을 살핀다", "Perception", characterSheetId.value(),
                        RuntimeCheckProposal.RollMethod.PLAYER, "1d20", 0, 10, List.of("evidence"),
                        "움직임을 알아차린다", "움직임을 놓친다"));
        UUID turnId = UUID.randomUUID();
        UUID commandId = UUID.randomUUID();
        RuntimeTurn requested = new RuntimeTurn(turnId, commandId, adventureId, sessionId.value(), packageId, 1,
                "저장고를 살핀다", new EvidencePack(List.of(), List.of(), List.of()), plan, null,
                adventure.currentContext(), List.of(), adventure.version(), List.of(), List.of(), false, true,
                RuntimeTurnOrigin.PLAYER, true, null, null, adventure.version(), false, false,
                RuntimeTurnLifecycle.REQUESTED, null, null, null, null, plan.narration());
        PendingRuntimeState pending = new PendingRuntimeState(GameStateDelta.empty(), DisclosureState.empty(),
                destination, List.of(), null, null);
        RuntimeTurn resolved = requested.pendingPlayerRoll(pending, CompletionProposal.continueAdventure(), plan.narration())
                .resolvePlayerRoll(15, 15, true);

        AdventureRepository adventures = mock(AdventureRepository.class);
        when(adventures.findById(adventureId)).thenReturn(Optional.of(adventure));
        ScenarioPackage scenarioPackage = mock(ScenarioPackage.class);
        ScenarioPackageRepository packages = mock(ScenarioPackageRepository.class);
        when(packages.findById(packageId)).thenReturn(Optional.of(scenarioPackage));
        RuntimeTurnRepository turns = mock(RuntimeTurnRepository.class);
        when(turns.findByCommandId(commandId)).thenReturn(Optional.of(resolved));
        AtomicReference<UUID> activatedMap = new AtomicReference<>();
        RuntimeTurnApplicationService service = new RuntimeTurnApplicationService(adventures,
                mock(RuntimeBindingRepository.class), packages, turns, mock(RuntimeEvidenceSearchPort.class),
                mock(RuntimePlanningPort.class), request -> new NarrationSafetyAssessment(true, "approved"),
                mock(SessionKnowledgeSetRepository.class));
        service.setCommitGate((committedAdventure, committedTurn) ->
                activatedMap.set(committedAdventure.currentSituation().mapDefinitionId()));

        service.retryPresentation(commandId);

        assertEquals(mapDefinitionId, activatedMap.get());
    }
}
