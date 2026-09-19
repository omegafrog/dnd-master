package com.dndmaster.diceroll.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndmaster.diceroll.application.DiceRandomPort;
import com.dndmaster.diceroll.application.DiceRollApplicationService;
import com.dndmaster.diceroll.application.DiceRollRepository;
import com.dndmaster.diceroll.domain.DiceRoll;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DiceRollControllerSecurityTest {
    @Test
    void player_roll_requires_internal_token_and_matching_idempotency_key() {
        DiceRollController controller = new DiceRollController(
                new DiceRollApplicationService(new EmptyRepository(), bound -> 0), new ApiRequestGuard("internal-secret"));
        UUID commandId = UUID.randomUUID();
        DiceRollController.DiceRollRequest request = new DiceRollController.DiceRollRequest(
                UUID.randomUUID(), UUID.randomUUID(), "PLAYER_ACTION", 1, 20, 0,
                UUID.randomUUID(), UUID.randomUUID(), commandId, 0L, "dnd5e.perception", 10);

        assertThrows(ApiRequestGuard.ApiContractException.class,
                () -> controller.playerRoll("wrong", commandId.toString(), request));
        assertThrows(ApiRequestGuard.ApiContractException.class,
                () -> controller.playerRoll("internal-secret", UUID.randomUUID().toString(), request));
    }

    @Test
    void enemy_observation_roll_validates_and_preserves_rule_reference_and_difficulty() {
        RecordingRepository repository = new RecordingRepository();
        DiceRollController controller = new DiceRollController(
                new DiceRollApplicationService(repository, bound -> 0), new ApiRequestGuard("internal-secret"));
        UUID commandId = UUID.randomUUID();
        DiceRollController.DiceRollRequest request = new DiceRollController.DiceRollRequest(
                UUID.randomUUID(), UUID.randomUUID(), "ENEMY", 1, 20, 2,
                UUID.randomUUID(), UUID.randomUUID(), commandId, 3L, "dnd5e.perception", 12);

        controller.enemyObservationRoll("internal-secret", commandId.toString(), request);

        assertEquals("dnd5e.perception", repository.saved.ruleReference());
        assertEquals(12, repository.saved.difficulty());
        assertThrows(ApiRequestGuard.ApiContractException.class, () -> controller.enemyObservationRoll(
                "internal-secret", commandId.toString(), new DiceRollController.DiceRollRequest(
                        request.adventureId(), request.ruleSetId(), "ENEMY", 1, 20, 2, request.sessionId(),
                        request.turnId(), UUID.randomUUID(), 3L, "", 12)));
    }

    private static final class EmptyRepository implements DiceRollRepository {
        @Override public Optional<DiceRoll> findByCommandId(UUID commandId) { return Optional.empty(); }
        @Override public void save(DiceRoll roll) { }
    }

    private static final class RecordingRepository implements DiceRollRepository {
        private DiceRoll saved;
        @Override public Optional<DiceRoll> findByCommandId(UUID commandId) { return Optional.empty(); }
        @Override public void save(DiceRoll roll) { saved = roll; }
    }
}
