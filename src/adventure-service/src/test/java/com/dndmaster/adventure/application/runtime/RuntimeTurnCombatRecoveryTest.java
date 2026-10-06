package com.dndmaster.adventure.application.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.dndmaster.adventure.application.combat.InMemoryEnemyCharacterSheetRepository;
import com.dndmaster.adventure.application.knowledge.SessionKnowledgeSetRepository;
import com.dndmaster.adventure.application.saved.AdventureRepository;
import com.dndmaster.adventure.application.scenario.compilation.ScenarioPackageRepository;
import com.dndmaster.adventure.domain.adventure.Adventure;
import com.dndmaster.adventure.domain.adventure.AdventureContext;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.CharacterSheetId;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import com.dndmaster.adventure.domain.adventure.RuleSetId;
import com.dndmaster.adventure.domain.adventure.RuntimeBinding;
import com.dndmaster.adventure.domain.adventure.ScenarioId;
import com.dndmaster.adventure.domain.adventure.SessionId;
import com.dndmaster.adventure.domain.scenario.ScenarioPackage;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RuntimeTurnCombatRecoveryTest {
    @Test
    void presented_combat_turn_reconstructs_its_enemy_sheet_preparation_request() {
        AdventureId adventureId = AdventureId.generate();
        OwnerPlayerId owner = new OwnerPlayerId(UUID.randomUUID());
        UUID packageId = UUID.randomUUID();
        UUID rulebookId = UUID.randomUUID();
        Adventure adventure = Adventure.create(adventureId, SessionId.generate(), owner, new ScenarioId(UUID.randomUUID()),
                new RuleSetId(UUID.randomUUID()), new CharacterSheetId(UUID.randomUUID()),
                new AdventureContext("저장고", "쥐 떼", "전투 시작", null));
        RuntimeBinding binding = mock(RuntimeBinding.class);
        when(binding.scenarioPackageId()).thenReturn(packageId);
        when(binding.rulebookIds()).thenReturn(List.of(rulebookId));
        when(binding.bindingVersion()).thenReturn(4L);
        ScenarioPackage scenarioPackage = mock(ScenarioPackage.class);
        when(scenarioPackage.packageId()).thenReturn(packageId);
        when(scenarioPackage.bundleId()).thenReturn(new ScenarioBundleId(UUID.randomUUID()));
        when(scenarioPackage.bundleRevision()).thenReturn(2L);

        AdventureRepository adventures = mock(AdventureRepository.class);
        when(adventures.findById(adventureId)).thenReturn(Optional.of(adventure));
        RuntimeBindingRepository bindings = mock(RuntimeBindingRepository.class);
        when(bindings.findCurrentByAdventureId(adventureId)).thenReturn(Optional.of(binding));
        ScenarioPackageRepository packages = mock(ScenarioPackageRepository.class);
        when(packages.findById(packageId)).thenReturn(Optional.of(scenarioPackage));
        RuntimeTurn turn = presentedCombatTurn(adventureId.value(), adventure.sessionId().value(), packageId);
        RuntimeTurnRepository turns = mock(RuntimeTurnRepository.class);
        when(turns.findByCommandId(turn.commandId())).thenReturn(Optional.of(turn));

        RuntimeTurnApplicationService service = new RuntimeTurnApplicationService(adventures, bindings, packages, turns,
                mock(RuntimeEvidenceSearchPort.class), mock(RuntimePlanningPort.class), mock(NarrationSafetyPort.class),
                mock(SessionKnowledgeSetRepository.class));
        service.setEnemyCharacterSheetRepository(new InMemoryEnemyCharacterSheetRepository());

        RuntimeTurnResult recovered = service.retryPresentation(turn.commandId());

        assertNotNull(recovered.enemySheetPreparationRequest());
        assertEquals(adventureId.value(), recovered.enemySheetPreparationRequest().adventureId());
        assertEquals("giant-rat", recovered.enemySheetPreparationRequest().enemies().getFirst().identity().enemyKind());
        assertEquals(List.of(rulebookId), recovered.enemySheetPreparationRequest().enemies().getFirst().identity().rulebookIds());
    }

    private static RuntimeTurn presentedCombatTurn(UUID adventureId, UUID sessionId, UUID packageId) {
        RuntimePlan plan = new RuntimePlan("저장고", null, "쥐를 발견했다", "거대 쥐가 다가온다", null,
                List.of(), List.of(), "codex-cli", "gpt", "", false, "", null, null,
                1, List.of(), null,
                List.of(new CombatEnemyProposal("encounter-rats", "giant-rat", "거대 쥐", 1)), true, false);
        return new RuntimeTurn(UUID.randomUUID(), UUID.randomUUID(), new AdventureId(adventureId), sessionId, packageId,
                4, "쥐를 발견한다", new EvidencePack(List.of(), List.of(), List.of()), plan, null,
                new AdventureContext("저장고", "쥐 떼", "쥐를 발견한다", "쥐를 발견했다"), List.of(), 1,
                List.of(), List.of(), true, true, RuntimeTurnOrigin.PLAYER, true, null, null,
                0L, false, false, RuntimeTurnLifecycle.PRESENTED, null);
    }
}
