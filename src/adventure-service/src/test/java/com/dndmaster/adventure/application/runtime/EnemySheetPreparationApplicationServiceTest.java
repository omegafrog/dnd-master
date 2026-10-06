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
