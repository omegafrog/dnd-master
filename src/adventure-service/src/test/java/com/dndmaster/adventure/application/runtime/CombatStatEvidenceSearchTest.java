package com.dndmaster.adventure.application.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dndmaster.adventure.application.knowledge.KnowledgeDocumentStatus;
import com.dndmaster.adventure.application.knowledge.SessionKnowledgeSetRepository;
import com.dndmaster.adventure.application.saved.AdventureRepository;
import com.dndmaster.adventure.application.scenario.compilation.ScenarioPackageRepository;
import com.dndmaster.adventure.domain.adventure.Adventure;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import com.dndmaster.adventure.domain.adventure.RuntimeBinding;
import com.dndmaster.adventure.domain.adventure.SessionId;
import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleDocumentRole;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleDocumentSelection;
import com.dndmaster.adventure.domain.scenario.ScenarioPackage;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CombatStatEvidenceSearchTest {
    @Test
    void searches_selected_rulebook_and_storybook_documents_for_the_same_enemy() {
        AdventureId adventureId = new AdventureId(UUID.randomUUID());
        OwnerPlayerId ownerId = new OwnerPlayerId(UUID.randomUUID());
        SessionId sessionId = new SessionId(UUID.randomUUID());
        UUID packageId = UUID.randomUUID();
        KnowledgeDocumentId rulebookId = new KnowledgeDocumentId(UUID.randomUUID());
        KnowledgeDocumentId storybookId = new KnowledgeDocumentId(UUID.randomUUID());
        Adventure adventure = mock(Adventure.class);
        when(adventure.id()).thenReturn(adventureId);
        when(adventure.sessionId()).thenReturn(sessionId);
        RuntimeBinding binding = mock(RuntimeBinding.class);
        when(binding.scenarioPackageId()).thenReturn(packageId);
        ScenarioPackage scenarioPackage = mock(ScenarioPackage.class);
        when(scenarioPackage.documents()).thenReturn(List.of(
                document(rulebookId, "RULEBOOK", ScenarioBundleDocumentRole.RULEBOOK),
                document(storybookId, "STORYBOOK", ScenarioBundleDocumentRole.MAIN_SCENARIO)));

        RuntimeEvidenceSearchPort search = mock(RuntimeEvidenceSearchPort.class);
        when(search.search(any())).thenAnswer(invocation -> {
            RuntimeEvidenceSearchRequest request = invocation.getArgument(0);
            UUID documentId = request.evidenceType() == RuntimeEvidenceType.RULEBOOK
                    ? rulebookId.value() : storybookId.value();
            return List.of(new RuntimeEvidence(request.evidenceType(), new KnowledgeDocumentId(documentId), 2,
                    "entry", "Giant Inferno Spider Armor Class 14 Hit Points 32. Melee Weapon Attack: +5 to hit."));
        });
        SessionKnowledgeSetRepository knowledge = mock(SessionKnowledgeSetRepository.class);
        when(knowledge.findBySessionId(sessionId)).thenReturn(Optional.empty());
        RuntimeTurnApplicationService service = new RuntimeTurnApplicationService(
                mock(AdventureRepository.class), mock(RuntimeBindingRepository.class),
                mock(ScenarioPackageRepository.class), mock(RuntimeTurnRepository.class), search,
                mock(RuntimePlanningPort.class), mock(NarrationSafetyPort.class), knowledge);
        SubmitRuntimeTurnCommand command = new SubmitRuntimeTurnCommand(adventureId, ownerId,
                UUID.randomUUID(), UUID.randomUUID(), "공격한다");
        CombatEnemyProposal enemy = new CombatEnemyProposal("", "giant-inferno-spider",
                "Giant Inferno Spider", 1, CombatStartMode.INSTANT);

        List<RuntimeEvidence> evidence = service.searchCombatStatEvidence(
                command, adventure, binding, scenarioPackage, List.of(enemy));

        assertThat(evidence).extracting(RuntimeEvidence::evidenceType)
                .containsExactly(RuntimeEvidenceType.RULEBOOK, RuntimeEvidenceType.STORYBOOK);
        ArgumentCaptor<RuntimeEvidenceSearchRequest> requests = ArgumentCaptor.forClass(RuntimeEvidenceSearchRequest.class);
        verify(search, org.mockito.Mockito.times(2)).search(requests.capture());
        assertThat(requests.getAllValues()).extracting(RuntimeEvidenceSearchRequest::evidenceType)
                .containsExactly(RuntimeEvidenceType.RULEBOOK, RuntimeEvidenceType.STORYBOOK);
        assertThat(requests.getAllValues()).extracting(RuntimeEvidenceSearchRequest::action)
                .containsOnly("giant-inferno-spider Giant Inferno Spider");
        assertThat(requests.getAllValues().get(0).knowledgeDocumentIds()).containsExactly(rulebookId.value());
        assertThat(requests.getAllValues().get(1).knowledgeDocumentIds()).containsExactly(storybookId.value());
    }

    private static ScenarioBundleDocumentSelection document(KnowledgeDocumentId id, String type,
            ScenarioBundleDocumentRole role) {
        return new ScenarioBundleDocumentSelection(id, role, KnowledgeDocumentStatus.INDEXED,
                type + ".pdf", type, 2);
    }
}
