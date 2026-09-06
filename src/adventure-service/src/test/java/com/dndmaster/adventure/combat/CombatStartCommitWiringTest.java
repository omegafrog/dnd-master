package com.dndmaster.adventure.combat;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.adventure.application.combat.CombatEncounterRepository;
import com.dndmaster.adventure.application.combat.CombatLifecycleApplicationService;
import com.dndmaster.adventure.application.combat.CombatWorkItemScheduler;
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
    void schedules_the_first_ai_turn_when_an_enemy_wins_initiative() {
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

        var encounter = service.startFromCommittedGmTurn(adventureId, committedTurn(), proposal);

        assertTrue(workItems.claim("test-worker", Duration.ofSeconds(10), Instant.now()).isPresent());
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
