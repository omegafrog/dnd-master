package com.dndmaster.adventure.combat;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.adventure.application.combat.CombatEncounterRepository;
import com.dndmaster.adventure.application.combat.CombatLifecycleApplicationService;
import com.dndmaster.adventure.application.combat.CombatWorkItemScheduler;
import com.dndmaster.adventure.application.combat.EnemyCharacterSheetIdentity;
import com.dndmaster.adventure.application.combat.EnemySheetPreparationRequest;
import com.dndmaster.adventure.application.combat.InMemoryCombatWorkItemRepository;
import com.dndmaster.adventure.application.saved.AdventureRepository;
import com.dndmaster.adventure.domain.adventure.Adventure;
import com.dndmaster.adventure.domain.adventure.AdventureContext;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.CharacterSheetId;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import com.dndmaster.adventure.domain.adventure.RuleSetId;
import com.dndmaster.adventure.domain.adventure.ScenarioId;
import com.dndmaster.adventure.domain.adventure.SessionId;
import com.dndmaster.adventure.domain.combat.CombatParticipant;
import com.dndmaster.adventure.domain.combat.CombatStartProposal;
import com.dndmaster.adventure.domain.runtime.GmInput;
import com.dndmaster.adventure.domain.runtime.GmTurn;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class CombatStartCommitWiringTest {
    @Test
    void only_committed_gm_turn_can_commit_typed_combat_start_proposal() {
        UUID adventureId = UUID.randomUUID();
        var repository = new RecordingCombatRepository();
        var service = new CombatLifecycleApplicationService(repository);
        var proposal = new CombatStartProposal(true, List.of(
                new CombatParticipant(UUID.randomUUID(), "Hero", CombatParticipant.Controller.PLAYER, 12, null)));
        var turn = GmTurn.start(UUID.randomUUID(), UUID.randomUUID(), 0,
                new GmInput.TextInput("fight")).process();

        assertThrows(IllegalStateException.class,
                () -> service.startFromCommittedGmTurn(adventureId, turn, proposal));
        assertEquals(0, repository.saved);
        var encounter = service.startFromCommittedGmTurn(adventureId,
                turn.commit("provider"), proposal);
        assertEquals(adventureId, encounter.adventureId());
        assertEquals(1, repository.saved);
    }

    @Test
    void schedules_the_first_ai_turn_only_after_result_processing_has_committed() {
        UUID adventureId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        UUID enemyId = UUID.randomUUID();
        var repository = new RecordingCombatRepository();
        var adventure = Adventure.create(new AdventureId(adventureId), new SessionId(UUID.randomUUID()),
                new OwnerPlayerId(playerId), new ScenarioId(UUID.randomUUID()), new RuleSetId(UUID.randomUUID()),
                new CharacterSheetId(playerId), new AdventureContext("전투", "위협", "대치", null));
        var workItems = new InMemoryCombatWorkItemRepository();
        var service = new CombatLifecycleApplicationService(repository, null, new AdventureStore(adventure), null, null,
                null, workItems, new CombatWorkItemScheduler(workItems, 20));
        var proposal = new CombatStartProposal(true, List.of(
                new CombatParticipant(enemyId, "적", CombatParticipant.Controller.AI, 20, null),
                new CombatParticipant(playerId, "영웅", CombatParticipant.Controller.PLAYER, 10, null)));

        var turn = committedTurn();
        var encounter = service.startFromCommittedGmTurn(adventureId, turn, proposal);

        assertTrue(workItems.claim("test-worker", Duration.ofSeconds(10), Instant.now()).isEmpty());
        assertTrue(service.scheduleFirstAiTurn(encounter, turn.commandId()));

        assertTrue(workItems.claim("test-worker", Duration.ofSeconds(10), Instant.now()).isPresent());
    }

    @Test
    void invalid_enemy_preparation_request_does_not_persist_preparing_encounter() {
        UUID adventureId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        var repository = new RecordingCombatRepository();
        var adventure = Adventure.create(new AdventureId(adventureId), new SessionId(UUID.randomUUID()),
                new OwnerPlayerId(playerId), new ScenarioId(UUID.randomUUID()), new RuleSetId(UUID.randomUUID()),
                new CharacterSheetId(playerId), new AdventureContext("전투", "위협", "대치", null));
        var workItems = new InMemoryCombatWorkItemRepository();
        var service = new CombatLifecycleApplicationService(repository, null, new AdventureStore(adventure), null, null,
                null, workItems, new CombatWorkItemScheduler(workItems, 20));
        UUID otherAdventureId = UUID.randomUUID();
        var proposal = new com.dndmaster.adventure.application.runtime.CombatEnemyProposal("scene", "goblin", "Goblin", 1);
        var request = new EnemySheetPreparationRequest(UUID.randomUUID(), adventureId,
                List.of(new EnemySheetPreparationRequest.Enemy(new EnemyCharacterSheetIdentity(otherAdventureId,
                        UUID.randomUUID(), 1, UUID.randomUUID(), List.of(UUID.randomUUID()), "goblin"), proposal)));
        var participants = List.of(new CombatParticipant(UUID.randomUUID(), "Goblin", CombatParticipant.Controller.AI,
                20, "enemy", com.dndmaster.adventure.domain.combat.TurnResources.initial(), null, null, "goblin"));

        assertThrows(IllegalArgumentException.class, () -> service.startPreparingFromCommittedGmTurn(adventureId,
                committedTurn(), participants, request));

        assertEquals(0, repository.saved);
        assertTrue(workItems.claim("worker", Duration.ofSeconds(10), Instant.now()).isEmpty());
    }

    @Test
    void enemy_preparation_carries_the_session_ai_request_id_through_to_follow_up_work() {
        UUID adventureId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        var repository = new RecordingCombatRepository();
        var adventure = Adventure.create(new AdventureId(adventureId), new SessionId(UUID.randomUUID()),
                new OwnerPlayerId(playerId), new ScenarioId(UUID.randomUUID()), new RuleSetId(UUID.randomUUID()),
                new CharacterSheetId(playerId), new AdventureContext("전투", "위협", "대치", null));
        var workItems = new InMemoryCombatWorkItemRepository();
        var service = new CombatLifecycleApplicationService(repository, null, new AdventureStore(adventure), null, null,
                null, workItems, new CombatWorkItemScheduler(workItems, 20));
        var turn = committedTurn();
        var proposal = new com.dndmaster.adventure.application.runtime.CombatEnemyProposal("scene", "goblin", "Goblin", 1);
        var request = new EnemySheetPreparationRequest(turn.turnId(), adventureId,
                List.of(new EnemySheetPreparationRequest.Enemy(new EnemyCharacterSheetIdentity(adventureId,
                        UUID.randomUUID(), 1, UUID.randomUUID(), List.of(UUID.randomUUID()), "goblin"), proposal)));
        var participants = List.of(new CombatParticipant(UUID.randomUUID(), "Goblin", CombatParticipant.Controller.AI,
                20, "enemy", com.dndmaster.adventure.domain.combat.TurnResources.initial(), null, null, "goblin"));

        service.startPreparingFromCommittedGmTurn(adventureId, turn, participants, request);

        var preparation = workItems.claim("worker", Duration.ofSeconds(10), Instant.now()).orElseThrow();
        assertEquals(turn.commandId(), preparation.aiRequestId());
    }

    private static GmTurn committedTurn() {
        return GmTurn.start(UUID.randomUUID(), UUID.randomUUID(), 0, new GmInput.TextInput("전투 시작")).process().commit("provider");
    }

    private static final class RecordingCombatRepository implements CombatEncounterRepository {
        private int saved;
        @Override public Optional<com.dndmaster.adventure.domain.combat.CombatEncounter> findActive(UUID adventureId) { return Optional.empty(); }
        @Override public com.dndmaster.adventure.domain.combat.CombatEncounter save(com.dndmaster.adventure.domain.combat.CombatEncounter encounter) { saved++; return encounter; }
    }

    private static final class AdventureStore implements AdventureRepository {
        private final Adventure adventure;
        private AdventureStore(Adventure adventure) { this.adventure = adventure; }
        @Override public Optional<Adventure> findById(AdventureId id) { return Optional.of(adventure); }
        @Override public List<Adventure> findSavedByOwner(OwnerPlayerId owner) { return List.of(adventure); }
        @Override public void save(Adventure value) { }
    }
}
