package com.dndmaster.adventure.combat;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dndmaster.adventure.application.combat.CombatEncounterRepository;
import com.dndmaster.adventure.application.combat.CombatLifecycleApplicationService;
import com.dndmaster.adventure.domain.combat.CombatParticipant;
import com.dndmaster.adventure.domain.combat.CombatStartProposal;
import com.dndmaster.adventure.domain.runtime.GmInput;
import com.dndmaster.adventure.domain.runtime.GmTurn;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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

    private static final class RecordingCombatRepository implements CombatEncounterRepository {
        private int saved;
        @Override public Optional<com.dndmaster.adventure.domain.combat.CombatEncounter> findActive(UUID adventureId) { return Optional.empty(); }
        @Override public com.dndmaster.adventure.domain.combat.CombatEncounter save(com.dndmaster.adventure.domain.combat.CombatEncounter encounter) { saved++; return encounter; }
    }
}
