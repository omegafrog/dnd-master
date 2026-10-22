package com.dndmaster.adventure.application.runtime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.dndmaster.adventure.application.combat.EnemyCharacterSheet;
import com.dndmaster.adventure.application.combat.EnemyCharacterSheetIdentity;
import com.dndmaster.adventure.application.combat.EnemySheetPreparationRequest;
import com.dndmaster.adventure.application.combat.InMemoryEnemyCharacterSheetRepository;
import com.dndmaster.adventure.application.knowledge.SessionKnowledgeSetRepository;
import com.dndmaster.adventure.application.saved.AdventureRepository;
import com.dndmaster.adventure.application.scenario.compilation.ScenarioPackageRepository;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import com.dndmaster.adventure.domain.combat.CombatEnemyStatBlock;
import com.dndmaster.adventure.domain.combat.CombatStatBlockSource;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EnemySheetPreparationApplicationServiceTest {
    @Test
    void saves_agent_contract_numbers_and_complete_sheet_using_only_supplied_source_keys() {
        var repository = new InMemoryEnemyCharacterSheetRepository();
        UUID sourceId = UUID.randomUUID();
        var evidence = new RuntimeEvidence(RuntimeEvidenceType.RULEBOOK,
                new com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId(sourceId), 2, "page=134", "앞 몬스터 다음에 거대 쥐의 수치가 있는 원문");
        var identity = new EnemyCharacterSheetIdentity(UUID.randomUUID(), UUID.randomUUID(), 1, UUID.randomUUID(), List.of(sourceId), "giant-rat");
        var requested = new CombatEnemyProposal("rats", "giant-rat", "거대 쥐", 2);
        var numbers = new CombatEnemyStatBlock(12, 7, 4, "1d4 + 2", new CombatStatBlockSource(sourceId, 2, "page=134", evidence.referenceKey()), 2);
        var candidate = requested.withStatBlock(numbers).withSheetCandidates(
                List.of("STR", "DEX", "CON", "INT", "WIS", "CHA").stream()
                        .map(name -> new CombatEnemyAbilityProposal(name, 10, List.of(evidence.referenceKey()))).toList(),
                List.of(new CombatEnemyActionProposal("물기", "명중 시 관통 피해", List.of(evidence.referenceKey()))));
        var planningPort = mock(RuntimePlanningPort.class);
        var planning = mock(RuntimePlanningRequest.class);
        org.mockito.Mockito.when(planning.evidencePack()).thenReturn(new EvidencePack(List.of(), List.of(evidence), List.of()));
        var response = mock(RuntimePlan.class);
        org.mockito.Mockito.when(response.combatEnemies()).thenReturn(List.of(candidate));
        org.mockito.Mockito.when(planningPort.prepareEnemySheets(planning)).thenReturn(response);
        var service = new RuntimeTurnApplicationService(mock(AdventureRepository.class), mock(RuntimeBindingRepository.class),
                mock(ScenarioPackageRepository.class), mock(RuntimeTurnRepository.class), mock(RuntimeEvidenceSearchPort.class),
                planningPort, mock(NarrationSafetyPort.class), mock(SessionKnowledgeSetRepository.class));
        service.setEnemyCharacterSheetRepository(repository);
        service.prepareEnemySheetsForWork(new EnemySheetPreparationRequest(UUID.randomUUID(), identity.adventureId(),
                List.of(new EnemySheetPreparationRequest.Enemy(identity, requested)), planning));
        org.junit.jupiter.api.Assertions.assertEquals(numbers, repository.find(identity).orElseThrow().statBlock());
        org.mockito.Mockito.verify(planningPort).prepareEnemySheets(planning);
    }

    @Test
    void attaches_contract_numbers_without_parsing_rulebook_lines_and_rejects_missing_numbers() {
        var requested = new CombatEnemyProposal("rats", "giant-rat", "거대 쥐", 2);
        var numbers = new CombatEnemyStatBlock(12, 7, 4, "1d4 + 2", new CombatStatBlockSource(UUID.randomUUID(), 2, "page=134"), 2);
        var candidate = requested.withStatBlock(numbers).withSheetCandidates(
                List.of("STR", "DEX", "CON", "INT", "WIS", "CHA").stream()
                        .map(name -> new CombatEnemyAbilityProposal(name, 10, List.of(numbers.source().citationKey()))).toList(),
                List.of(new CombatEnemyActionProposal("물기", "명중 시 관통 피해", List.of(numbers.source().citationKey()))));
        var result = RuntimeTurnApplicationService.attachEnemySheetCandidates(List.of(requested), List.of(candidate), List.of("giant-rat"));
        org.junit.jupiter.api.Assertions.assertEquals(numbers, result.get(0).statBlock());
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> RuntimeTurnApplicationService.attachEnemySheetCandidates(List.of(requested),
                        List.of(candidate.withStatBlock(null)), List.of("giant-rat")));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> RuntimeTurnApplicationService.attachEnemySheetCandidates(List.of(requested),
                        List.of(candidate, candidate), List.of("giant-rat")));
    }

    @Test
    void reuses_all_prepared_sheets_without_requiring_a_candidate_planning_request() {
        var repository = new InMemoryEnemyCharacterSheetRepository();
        var identity = new EnemyCharacterSheetIdentity(UUID.randomUUID(), UUID.randomUUID(), 1, UUID.randomUUID(),
                List.of(UUID.randomUUID()), "goblin");
        repository.saveIfAbsent(new EnemyCharacterSheet(identity, "Goblin",
                new CombatEnemyStatBlock(12, 7, 2, "1d6", new CombatStatBlockSource(UUID.randomUUID(), 1, "rule")),
                List.of("STR", "DEX", "CON", "INT", "WIS", "CHA").stream()
                        .map(name -> new CombatEnemyAbilityProposal(name, 10, List.of("rule"))).toList(),
                List.of(new EnemyCharacterSheet.EnemyCombatAction("Scimitar", "Melee strike", List.of("rule")))));
        var planningPort = mock(RuntimePlanningPort.class);
        var service = new RuntimeTurnApplicationService(mock(AdventureRepository.class),
                mock(RuntimeBindingRepository.class), mock(ScenarioPackageRepository.class), mock(RuntimeTurnRepository.class),
                mock(RuntimeEvidenceSearchPort.class), planningPort, mock(NarrationSafetyPort.class),
                mock(SessionKnowledgeSetRepository.class));
        service.setEnemyCharacterSheetRepository(repository);
        var request = new EnemySheetPreparationRequest(UUID.randomUUID(), identity.adventureId(),
                List.of(new EnemySheetPreparationRequest.Enemy(identity,
                        new CombatEnemyProposal("scene", "goblin", "Goblin", 1))));

        assertDoesNotThrow(() -> service.prepareEnemySheetsForWork(request));
        verifyNoInteractions(planningPort);
    }
}
