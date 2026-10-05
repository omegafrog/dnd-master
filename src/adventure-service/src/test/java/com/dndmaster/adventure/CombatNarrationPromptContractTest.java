package com.dndmaster.adventure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dndmaster.adventure.application.saved.AdventureRepository;
import com.dndmaster.adventure.application.runtime.DefaultNarrationSafetyPolicy;
import com.dndmaster.adventure.application.runtime.NarrationSafetyPort;
import com.dndmaster.adventure.application.runtime.RuntimeBindingRepository;
import com.dndmaster.adventure.application.runtime.RuntimeCharacterSheetReadPort;
import com.dndmaster.adventure.application.runtime.RuntimeEvidenceSearchPort;
import com.dndmaster.adventure.application.runtime.RuntimeEvidence;
import com.dndmaster.adventure.application.runtime.RuntimeEvidenceType;
import com.dndmaster.adventure.application.runtime.GmAgentRuntimePlanningAdapter;
import com.dndmaster.adventure.application.runtime.GmFinalValidator;
import com.dndmaster.adventure.application.runtime.GmPlanResult;
import com.dndmaster.adventure.application.runtime.RuntimeGmPromptComposer;
import com.dndmaster.adventure.application.runtime.RuntimePlan;
import com.dndmaster.adventure.application.runtime.RuntimePlanningPort;
import com.dndmaster.adventure.application.runtime.RuntimeTurnApplicationService;
import com.dndmaster.adventure.application.runtime.RuntimeFactLookupService;
import com.dndmaster.adventure.application.runtime.RuntimeFactLookupRequest;
import com.dndmaster.adventure.application.runtime.RuntimeTurnRepository;
import com.dndmaster.adventure.application.scenario.compilation.ScenarioPackageRepository;
import com.dndmaster.adventure.application.knowledge.SessionKnowledgeSetRepository;
import com.dndmaster.adventure.application.combat.CombatActionCommand;
import com.dndmaster.adventure.application.combat.CombatActorRole;
import com.dndmaster.adventure.application.combat.CombatNarrationRequest;
import com.dndmaster.adventure.application.combat.ConfirmedCombatState;
import com.dndmaster.adventure.domain.adventure.Adventure;
import com.dndmaster.adventure.domain.adventure.AdventureContext;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.AdventurePartyMember;
import com.dndmaster.adventure.domain.adventure.AdventureStatus;
import com.dndmaster.adventure.domain.adventure.CharacterSheetId;
import com.dndmaster.adventure.domain.adventure.ControlMode;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import com.dndmaster.adventure.domain.adventure.RuleSetId;
import com.dndmaster.adventure.domain.adventure.RuntimeBinding;
import com.dndmaster.adventure.domain.adventure.ScenarioId;
import com.dndmaster.adventure.domain.adventure.SessionId;
import com.dndmaster.adventure.domain.scenario.ScenarioPackage;
import com.dndmaster.adventure.domain.scenario.ScenarioModel;
import com.dndmaster.adventure.domain.scenario.ScenarioModelElement;
import com.dndmaster.adventure.domain.scenario.ScenarioSourceReference;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleDocumentSelection;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleDocumentRole;
import com.dndmaster.adventure.application.knowledge.KnowledgeDocumentStatus;
import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import com.dndmaster.adventure.domain.adventure.ConversationEntry;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class CombatNarrationPromptContractTest {
    @Test
    void confirmed_combat_narration_receives_confirmation_recent_dialogue_and_pending_roll_in_context() {
        UUID adventureUuid = UUID.randomUUID();
        UUID playerUuid = UUID.randomUUID();
        UUID sessionUuid = UUID.randomUUID();
        UUID packageUuid = UUID.randomUUID();
        UUID sheetUuid = UUID.randomUUID();
        UUID storybookUuid = UUID.randomUUID();
        String hiddenFact = "The caretaker is secretly the missing heir.";
        String hiddenElementId = "secret-revelation-17";
        String hiddenLocator = "page:secret-revelation";
        KnowledgeDocumentId storybookId = new KnowledgeDocumentId(storybookUuid);
        RuntimeEvidence hiddenEvidence = new RuntimeEvidence(RuntimeEvidenceType.STORYBOOK, storybookId, 2,
                hiddenLocator, hiddenFact, "secret-citation");
        RuntimeEvidence allowedEvidence = new RuntimeEvidence(RuntimeEvidenceType.STORYBOOK, storybookId, 2,
                "page:gate-description", "A stone gate stands beneath a weathered crest.", "gate-citation");
        ScenarioModel scenarioModel = new ScenarioModel(1,
                List.of(new ScenarioModelElement("gatekeeper", "actor", java.util.Map.of("name", "보초"), List.of())),
                List.of(), List.of(), List.of(new ScenarioModelElement(hiddenElementId, "revelation",
                        java.util.Map.of("value", hiddenFact, "status", "UNKNOWN", "elementCode", "R-17"),
                        List.of(new ScenarioSourceReference(storybookId, 2, hiddenLocator)))),
                List.of(), List.of(), List.of(), "성문 앞에서 추격자가 다가온다.");
        AdventureId adventureId = new AdventureId(adventureUuid);
        OwnerPlayerId owner = new OwnerPlayerId(playerUuid);
        SessionId session = new SessionId(sessionUuid);
        CharacterSheetId sheet = new CharacterSheetId(sheetUuid);
        List<ConversationEntry> existingConversation = List.of(
                new ConversationEntry(0, "PLAYER", "문지기에게 길을 묻는다."),
                new ConversationEntry(1, "AI_GAME_MASTER", "문지기가 성문을 지키고 있다."));
        RuleSetId ruleSetId = new RuleSetId(UUID.randomUUID());
        List<AdventurePartyMember> party = List.of(new AdventurePartyMember(sheet, ControlMode.DIRECT, true, true, true, true, true, true));
        Adventure adventure = Adventure.rehydrate(adventureId, session, owner, new ScenarioId(UUID.randomUUID()), ruleSetId,
                party, existingConversation, new AdventureContext("성문", null, null, null), AdventureStatus.ACTIVE, 2L);
        RuntimeBinding binding = mock(RuntimeBinding.class);
        ScenarioPackage scenarioPackage = mock(ScenarioPackage.class);
        AdventureRepository adventures = mock(AdventureRepository.class);
        when(adventures.findById(adventureId)).thenReturn(Optional.of(adventure));

        RuntimeBindingRepository bindings = mock(RuntimeBindingRepository.class);
        when(bindings.findCurrentByAdventureId(adventureId)).thenReturn(Optional.of(binding));
        when(binding.scenarioPackageId()).thenReturn(packageUuid);
        when(binding.bindingVersion()).thenReturn(1L);
        when(binding.activeSourceContext()).thenReturn(null);
        ScenarioPackageRepository packages = mock(ScenarioPackageRepository.class);
        when(packages.findById(packageUuid)).thenReturn(Optional.of(scenarioPackage));
        when(scenarioPackage.documents()).thenReturn(List.of(new ScenarioBundleDocumentSelection(storybookId,
                ScenarioBundleDocumentRole.MAIN_SCENARIO, KnowledgeDocumentStatus.INDEXED,
                "main-story.pdf", "STORYBOOK", 2)));
        when(scenarioPackage.runtimeCandidates()).thenReturn(List.of());
        when(scenarioPackage.scenarioModel()).thenReturn(scenarioModel);
        SessionKnowledgeSetRepository knowledge = mock(SessionKnowledgeSetRepository.class);
        when(knowledge.findBySessionId(session)).thenReturn(Optional.empty());
        RuntimeTurnRepository turns = mock(RuntimeTurnRepository.class);
        var pending = mock(com.dndmaster.adventure.application.runtime.RuntimeTurn.class);
        when(pending.lifecycle()).thenReturn(com.dndmaster.adventure.application.runtime.RuntimeTurnLifecycle.PENDING_ROLL);
        when(pending.action()).thenReturn("Perception: inspect the gate");
        when(turns.findAllByAdventureId(adventureId)).thenReturn(List.of(pending));
        RuntimeEvidenceSearchPort evidenceSearch = mock(RuntimeEvidenceSearchPort.class);
        when(evidenceSearch.search(any())).thenReturn(List.of(hiddenEvidence, allowedEvidence));
        AtomicReference<String> prompt = new AtomicReference<>();
        RuntimePlanningPort planning = new GmAgentRuntimePlanningAdapter(context -> {
            verify(adventures).save(argThat(saved -> saved.conversation().stream()
                    .anyMatch(entry -> entry.content().startsWith("확정 전투 결과:"))
                    && saved.conversation().stream().noneMatch(entry -> entry.content().contains("현재 HP=4/7"))));
            prompt.set(RuntimeGmPromptComposer.compose(context, 128_000));
            return new GmPlanResult(new RuntimePlan("성문", null, "명중", "검이 갑옷을 두드린다.",
                    null, List.of(), List.of()), "provider", "model", "reasoning", List.of());
        }, new GmFinalValidator());
        RuntimeTurnApplicationService service = new RuntimeTurnApplicationService(
                adventures, bindings, packages, turns, evidenceSearch, planning,
                new DefaultNarrationSafetyPolicy(), knowledge);
        RuntimeFactLookupService factLookup = mock(RuntimeFactLookupService.class);
        service.setRuntimeFactLookupService(factLookup);
        service.setCharacterSheetReadPort(ignored -> "현재 시트");
        CombatActionCommand command = new CombatActionCommand(UUID.randomUUID(), adventureId, sessionUuid,
                new RuleSetId(UUID.randomUUID()), sheet, null, CombatActorRole.PLAYER, "ATTACK", null,
                playerUuid, null, 1);

        ConfirmedCombatState combatState = new ConfirmedCombatState(3, List.of(
                new ConfirmedCombatState.Enemy(UUID.randomUUID(), "고블린", 4, 7, false),
                new ConfirmedCombatState.Enemy(UUID.randomUUID(), "거대 쥐", 0, 5, true)));
        service.narrateConfirmedCombat(CombatNarrationRequest.postResolution(command, 3, combatState, 17, "명중", "검을 휘두른다."));

        assertThat(prompt.get()).contains("확정된 전투 행동", "주사위 결과=17", "판정=명중");
        assertThat(prompt.get()).contains("고블린", "현재 HP=4/7", "거대 쥐", "현재 HP=0/5", "쓰러짐");
        assertThat(prompt.get()).contains("PLAYER: 검을 휘두른다.");
        assertThat(prompt.get()).contains(
                "PLAYER: 문지기에게 길을 묻는다.",
                "AI_GAME_MASTER: 문지기가 성문을 지키고 있다.");
        assertThat(prompt.get()).contains("확정 전투 결과:", "PENDING_ROLL: Perception: inspect the gate");
        assertThat(prompt.get()).contains("A stone gate stands beneath a weathered crest.");
        assertThat(prompt.get()).contains("성문 앞에서 추격자가 다가온다.",
                "고정 지침·잠긴 자료", "현재 상황 관련 장기 기록", "압축된 이전 대화",
                "압축하지 않은 최근 대화", "최신 캐릭터 시트·Current Situation·이번 턴 근거·플레이어 입력");
        assertThat(prompt.get()).doesNotContain(hiddenFact, hiddenElementId, hiddenLocator, "secret-citation", "UNKNOWN", "R-17");
        verify(evidenceSearch).search(any());
        verify(factLookup, never()).lookup(any(RuntimeFactLookupRequest.class), anyList());
        verify(adventures, atLeastOnce()).save(argThat(saved -> saved.conversation().stream()
                .filter(entry -> entry.content().startsWith("확정 전투 결과:"))
                .noneMatch(entry -> entry.content().contains("현재 HP=") || entry.content().contains("HP=4/7"))));
    }
}
