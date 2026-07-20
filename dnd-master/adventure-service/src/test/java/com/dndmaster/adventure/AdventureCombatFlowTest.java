package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndmaster.adventure.application.combat.AdventureCombatApplicationService;
import com.dndmaster.adventure.application.combat.AiCombatPort;
import com.dndmaster.adventure.application.combat.CombatActionCommand;
import com.dndmaster.adventure.application.combat.CombatActorRole;
import com.dndmaster.adventure.application.combat.CombatIdempotencyConflictException;
import com.dndmaster.adventure.application.combat.CombatOperation;
import com.dndmaster.adventure.application.combat.CombatOperationRepository;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.CharacterSheetId;
import com.dndmaster.adventure.domain.adventure.RuleSetId;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AdventureCombatFlowTest {
    @Test
    void composes_character_role_roll_movement_ai_state_and_persists_one_result() {
        var repository = new MemoryRepository();
        var calls = new Calls();
        var service = service(repository, calls);
        CombatActionCommand command = command(UUID.randomUUID(), CombatActorRole.PLAYER, "A1>B1");

        var result = service.resolveCombatAction(command);
        var duplicate = service.resolveCombatAction(command);

        assertEquals(18, result.diceTotal());
        assertEquals("hit", result.judgment());
        assertEquals(result, duplicate);
        assertEquals(1, calls.character);
        assertEquals(1, calls.dice);
        assertEquals(1, calls.map);
        assertEquals(1, calls.state);
        assertEquals(1, calls.adjudication);
    }

    @Test
    void passes_each_actor_role_to_the_role_aware_dice_contract() {
        var repository = new MemoryRepository();
        var calls = new Calls();
        var service = service(repository, calls);

        for (CombatActorRole role : CombatActorRole.values()) {
            service.resolveCombatAction(command(UUID.randomUUID(), role, null));
            assertEquals(role, calls.lastRole);
        }
        assertEquals(4, calls.dice);
        assertEquals(0, calls.map);
    }

    @Test
    void rejects_reusing_an_operation_id_for_different_combat_payload() {
        var service = service(new MemoryRepository(), new Calls());
        UUID operationId = UUID.randomUUID();
        service.resolveCombatAction(command(operationId, CombatActorRole.PLAYER, null));

        assertThrows(CombatIdempotencyConflictException.class, () -> service.resolveCombatAction(
                new CombatActionCommand(operationId, AdventureId.generate(), new RuleSetId(UUID.randomUUID()),
                        new CharacterSheetId(UUID.randomUUID()), CombatActorRole.PLAYER, "different", null)));
    }

    private static AdventureCombatApplicationService service(MemoryRepository repository, Calls calls) {
        AiCombatPort ai = new AiCombatPort() {
            @Override public void controlState(CombatActionCommand command) { calls.state++; }
            @Override public String adjudicate(CombatActionCommand command, int diceTotal) {
                calls.adjudication++;
                return "hit";
            }
        };
        return new AdventureCombatApplicationService(
                repository,
                command -> calls.character++,
                command -> { calls.dice++; calls.lastRole = command.role(); return 18; },
                command -> calls.map++,
                ai);
    }

    private static CombatActionCommand command(UUID operationId, CombatActorRole role, String movement) {
        return new CombatActionCommand(
                operationId, AdventureId.generate(), new RuleSetId(UUID.randomUUID()),
                new CharacterSheetId(UUID.randomUUID()), role, "attack", movement);
    }

    private static final class Calls {
        private int character;
        private int dice;
        private int map;
        private int state;
        private int adjudication;
        private CombatActorRole lastRole;
    }

    private static final class MemoryRepository implements CombatOperationRepository {
        private final Map<UUID, CombatOperation> values = new HashMap<>();
        @Override public Optional<CombatOperation> findById(UUID operationId) { return Optional.ofNullable(values.get(operationId)); }
        @Override public void save(CombatOperation operation) { values.put(operation.id(), operation); }
    }
}
