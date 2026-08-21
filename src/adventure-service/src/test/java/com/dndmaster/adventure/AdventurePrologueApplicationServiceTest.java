package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import com.dndmaster.adventure.application.prologue.*;
import com.dndmaster.adventure.application.runtime.CharacterSheetReadPort;
import com.dndmaster.adventure.application.runtime.EvidencePack;
import com.dndmaster.adventure.application.runtime.GmAgentPort;
import com.dndmaster.adventure.application.runtime.GmPlanResult;
import com.dndmaster.adventure.application.runtime.GmContextEnvelope;
import com.dndmaster.adventure.application.runtime.RuntimeEvidence;
import com.dndmaster.adventure.application.runtime.RuntimeEvidenceSearchPort;
import com.dndmaster.adventure.application.runtime.RuntimeEvidenceSearchRequest;
import com.dndmaster.adventure.application.runtime.RuntimeEvidenceType;
import com.dndmaster.adventure.application.runtime.RuntimePlan;
import com.dndmaster.adventure.application.scenario.compilation.ScenarioPackageRepository;
import com.dndmaster.adventure.application.saved.AdventureRepository;
import com.dndmaster.adventure.application.storyplan.AdventureStoryPlanRepository;
import com.dndmaster.adventure.domain.adventure.*;
import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleDocumentRole;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleDocumentSelection;
import com.dndmaster.adventure.application.knowledge.KnowledgeDocumentStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AdventurePrologueApplicationServiceTest {
    @Test
    void creates_one_grounded_prologue_from_current_stage_and_sheet_snapshot() {
        var owner = new OwnerPlayerId(UUID.randomUUID());
        var sheetId = new CharacterSheetId(UUID.randomUUID());
        var adventure = Adventure.create(new AdventureId(UUID.randomUUID()), SessionId.generate(), owner,
                new ScenarioId(UUID.randomUUID()), new RuleSetId(UUID.randomUUID()),
                List.of(new AdventurePartyMember(sheetId, ControlMode.DIRECT, false, false, false, false, false, false)),
                new AdventureContext("opening", null, null, null));
        var stage = new AdventureStoryPlanStage(1, "The Bell", "Find the bell", "A warning sounds", "Reach the tower", List.of("bell"), List.of("safe"));
        var plan = AdventureStoryPlan.ready(adventure.sessionId(), 0, 1, List.of(stage));
        var adventures = mock(AdventureRepository.class);
        var plans = mock(AdventureStoryPlanRepository.class);
        var sheets = mock(CharacterSheetReadPort.class);
        var generator = mock(AdventurePrologueGenerationPort.class);
        when(adventures.findById(adventure.id())).thenReturn(Optional.of(adventure));
        when(plans.findBySessionId(adventure.sessionId())).thenReturn(Optional.of(plan));
        when(sheets.read(sheetId)).thenReturn(new CharacterSheetReadPort.CharacterSheet(sheetId, "Mira", 2));
        when(generator.generate(any())).thenReturn("Mira hears the bell. The warning sounds from the tower.");

        new AdventurePrologueApplicationService(adventures, plans, sheets, generator).ensure(adventure.id(), owner);

        assertEquals(List.of(new ConversationEntry(0, "AI_GAME_MASTER", "Mira hears the bell. The warning sounds from the tower.")), adventure.conversation());
        verify(adventures).save(adventure);
        new AdventurePrologueApplicationService(adventures, plans, sheets, generator).ensure(adventure.id(), owner);
        verify(generator, times(1)).generate(any());
    }

    @Test
    void passes_storybook_and_rulebook_evidence_to_gm_prologue() {
        var owner = new OwnerPlayerId(UUID.randomUUID());
        var sheetId = new CharacterSheetId(UUID.randomUUID());
        var packageId = UUID.randomUUID();
        var storybookId = UUID.randomUUID();
        var rulebookId = UUID.randomUUID();
        var adventure = Adventure.create(new AdventureId(UUID.randomUUID()), SessionId.generate(), owner,
                new ScenarioId(packageId), new RuleSetId(UUID.randomUUID()),
                List.of(new AdventurePartyMember(sheetId, ControlMode.DIRECT, false, false, false, false, false, false)),
                new AdventureContext("opening", null, null, null));
        var stage = new AdventureStoryPlanStage(1, "양조장", "흔적 찾기", "정보 부족", "단서 확보", List.of("기록"), List.of("ending-1"));
        var plan = AdventureStoryPlan.ready(adventure.sessionId(), 0, 1, List.of(stage));
        var adventures = mock(AdventureRepository.class);
        var plans = mock(AdventureStoryPlanRepository.class);
        var sheets = mock(CharacterSheetReadPort.class);
        var generator = mock(AdventurePrologueGenerationPort.class);
        var gmAgent = mock(GmAgentPort.class);
        var packages = mock(ScenarioPackageRepository.class);
        var evidenceSearch = mock(RuntimeEvidenceSearchPort.class);
        var scenarioPackage = mock(com.dndmaster.adventure.domain.scenario.ScenarioPackage.class);
        var documents = List.of(
                new ScenarioBundleDocumentSelection(new KnowledgeDocumentId(storybookId), ScenarioBundleDocumentRole.MAIN_SCENARIO,
                        KnowledgeDocumentStatus.INDEXED, "story.pdf", "STORYBOOK", 1),
                new ScenarioBundleDocumentSelection(new KnowledgeDocumentId(rulebookId), ScenarioBundleDocumentRole.RULEBOOK,
                        KnowledgeDocumentStatus.INDEXED, "rules.pdf", "RULEBOOK", 1));
        var storyEvidence = new RuntimeEvidence(RuntimeEvidenceType.STORYBOOK, new KnowledgeDocumentId(storybookId), 1, "page:1", "양조장 기록");
        var ruleEvidence = new RuntimeEvidence(RuntimeEvidenceType.RULEBOOK, new KnowledgeDocumentId(rulebookId), 1, "chapter:1", "판정 규칙");
        when(adventures.findById(adventure.id())).thenReturn(Optional.of(adventure));
        when(plans.findBySessionId(adventure.sessionId())).thenReturn(Optional.of(plan));
        when(sheets.read(sheetId)).thenReturn(new CharacterSheetReadPort.CharacterSheet(sheetId, "Mira", 2));
        when(packages.findById(packageId)).thenReturn(Optional.of(scenarioPackage));
        when(scenarioPackage.documents()).thenReturn(documents);
        when(evidenceSearch.search(any(RuntimeEvidenceSearchRequest.class))).thenAnswer(invocation ->
                ((RuntimeEvidenceSearchRequest) invocation.getArgument(0)).evidenceType() == RuntimeEvidenceType.STORYBOOK
                        ? List.of(storyEvidence) : List.of(ruleEvidence));
        when(gmAgent.plan(any(GmContextEnvelope.class))).thenAnswer(invocation -> {
            GmContextEnvelope context = invocation.getArgument(0);
            assertFalse(context.evidencePack().storybook().isEmpty());
            assertFalse(context.evidencePack().rulebook().isEmpty());
            return new GmPlanResult(new RuntimePlan("opening", "", "판단", "장면", null, context.evidencePack().storybook(), List.of()),
                    "test", "test", "", List.of());
        });

        new AdventurePrologueApplicationService(adventures, plans, sheets, generator, gmAgent, packages, evidenceSearch)
                .ensure(adventure.id(), owner);

        verify(gmAgent).plan(any(GmContextEnvelope.class));
        verify(evidenceSearch, times(2)).search(any(RuntimeEvidenceSearchRequest.class));
    }
}
