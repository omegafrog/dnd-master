package com.dndmaster.adventure.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.adventure.application.combat.AdventureCombatApplicationService;
import com.dndmaster.adventure.application.combat.AiCombatPort;
import com.dndmaster.adventure.application.combat.CombatActionCommand;
import com.dndmaster.adventure.application.combat.CombatActorRole;
import com.dndmaster.adventure.application.combat.CombatIdempotencyConflictException;
import com.dndmaster.adventure.application.combat.CombatOperation;
import com.dndmaster.adventure.application.combat.CombatOperationRepository;
import com.dndmaster.adventure.application.combat.CharacterCombatPort;
import com.dndmaster.adventure.application.combat.CombatCharacterMutation;
import com.dndmaster.adventure.application.combat.CombatOutcome;
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

    @Test
    void applies_structured_character_effect_once_after_adjudication() {
        var repository = new MemoryRepository();
        var calls = new Calls();
        AiCombatPort ai = new AiCombatPort() {
            @Override public void controlState(CombatActionCommand command) { calls.state++; }
            @Override public String adjudicate(CombatActionCommand command, int diceTotal) { return "hit"; }
            @Override public CombatOutcome adjudicateOutcome(CombatActionCommand command, int diceTotal) {
                return new CombatOutcome("hit", new CombatCharacterMutation(-3, 10, java.util.List.of("healing potion"), java.util.List.of()));
            }
        };
        var service = new AdventureCombatApplicationService(repository, new CharacterCombatPort() {
                    @Override public void requireUsableCharacter(CombatActionCommand command) {}
                    @Override public void applyOutcome(CombatActionCommand command, CombatOutcome outcome) {
                        calls.applied++;
                        calls.mutation = outcome.mutation();
                    }
                }, command -> 18,
                command -> {}, ai);
        CombatActionCommand command = command(UUID.randomUUID(), CombatActorRole.PLAYER, null);

        service.resolveCombatAction(command);
        service.resolveCombatAction(command);

        assertEquals(1, calls.applied);
        assertEquals(-3, calls.mutation.hitPointDelta());
        assertEquals(10, calls.mutation.currencyDelta());
        assertEquals(java.util.List.of("healing potion"), calls.mutation.addItems());
    }

    @Test
    void legacy_prose_adjudication_cannot_mutate_character_sheet() {
        var calls = new Calls();
        var service = service(new MemoryRepository(), calls);

        service.resolveCombatAction(command(UUID.randomUUID(), CombatActorRole.PLAYER, null));

        assertEquals(1, calls.applied);
        assertTrue(!calls.mutation.hasEffects());
    }

    @Test
    void ended_outcome_is_exposed_and_replayed_without_reapplying_roll_or_effect() {
        var repository = new MemoryRepository();
        var calls = new Calls();
        AiCombatPort ai = new AiCombatPort() {
            @Override public void controlState(CombatActionCommand command) { calls.state++; }
            @Override public String adjudicate(CombatActionCommand command, int diceTotal) { return "legacy"; }
            @Override public CombatOutcome adjudicateOutcome(CombatActionCommand command, int diceTotal) {
                calls.adjudication++;
                return new CombatOutcome("enemy defeated", new CombatCharacterMutation(0, 5,
                        java.util.List.of("trophy"), java.util.List.of()), true);
            }
        };
        var service = new AdventureCombatApplicationService(repository, new CharacterCombatPort() {
            @Override public void requireUsableCharacter(CombatActionCommand command) { calls.character++; }
            @Override public void applyOutcome(CombatActionCommand command, CombatOutcome outcome) { calls.applied++; }
        }, command -> { calls.dice++; return 15; }, command -> {}, ai);
        CombatActionCommand command = command(UUID.randomUUID(), CombatActorRole.PLAYER, null);

        var first = service.resolveCombatAction(command);
        var replay = service.resolveCombatAction(command);

        assertEquals("COMBAT_ENDED", first.resolutionStatus());
        assertTrue(first.outcomeApplied());
        assertEquals(first, replay);
        assertEquals(1, calls.dice);
        assertEquals(1, calls.adjudication);
        assertEquals(1, calls.applied);
    }

    @Test
    void legacy_adjudication_never_claims_combat_ended() {
        var calls = new Calls();
        var result = service(new MemoryRepository(), calls)
                .resolveCombatAction(command(UUID.randomUUID(), CombatActorRole.PLAYER, null));

        assertEquals("RESOLVED", result.resolutionStatus());
    }

    @Test
    void configured_adjudicator_distinguishes_natural_twenty_one_and_deferred_rolls() {
        int[] rolls = {20, 1, 12};
        int[] nextRoll = {0};
        AiCombatPort ai = new AiCombatPort() {
            @Override public void controlState(CombatActionCommand command) {}
            @Override public String adjudicate(CombatActionCommand command, int diceTotal) {
                if (diceTotal == 20) return "critical hit (natural 20)";
                if (diceTotal == 1) return "critical miss (natural 1)";
                return "판정 보류: 대상 AC와 공격 보정이 필요합니다 (d20=" + diceTotal + ").";
            }
        };
        var service = new AdventureCombatApplicationService(new MemoryRepository(),
                command -> {}, command -> rolls[nextRoll[0]++], command -> {}, ai);

        var criticalHit = service.resolveCombatAction(command(UUID.randomUUID(), CombatActorRole.PLAYER, null));
        assertEquals("critical hit (natural 20)", criticalHit.judgment());
        assertEquals("RESOLVED", criticalHit.resolutionStatus());
        assertTrue(criticalHit.outcomeApplied());

        var criticalMiss = service.resolveCombatAction(command(UUID.randomUUID(), CombatActorRole.PLAYER, null));
        assertEquals("critical miss (natural 1)", criticalMiss.judgment());
        assertEquals("RESOLVED", criticalMiss.resolutionStatus());
        assertTrue(criticalMiss.outcomeApplied());

        var pending = service.resolveCombatAction(command(UUID.randomUUID(), CombatActorRole.PLAYER, null));
        assertEquals("판정 보류: 대상 AC와 공격 보정이 필요합니다 (d20=12).", pending.judgment());
        assertEquals("PENDING_RULE_INPUT", pending.resolutionStatus());
        assertTrue(!pending.outcomeApplied());
    }

    @Test
    void configured_critical_hit_applies_double_damage_and_preserves_end_combat() {
        var calls = new Calls();
        var service = new AdventureCombatApplicationService(new MemoryRepository(),
                new CharacterCombatPort() {
                    @Override public void requireUsableCharacter(CombatActionCommand command) {}
                    @Override public void applyOutcome(CombatActionCommand command, CombatOutcome outcome) {
                        calls.mutation = outcome.mutation();
                    }
                }, command -> 20, command -> {}, new com.dndmaster.adventure.api.AdventureApiConfiguration().aiCombatPort());

        var result = service.resolveCombatAction(commandWithDamage(UUID.randomUUID(), 7, true));

        assertEquals("critical hit (natural 20)", result.judgment());
        assertEquals("COMBAT_ENDED", result.resolutionStatus());
        assertTrue(result.outcomeApplied());
        assertEquals(-14, calls.mutation.hitPointDelta());
    }

    @Test
    void configured_critical_miss_does_not_apply_damage_or_end_combat() {
        var calls = new Calls();
        var service = new AdventureCombatApplicationService(new MemoryRepository(),
                new CharacterCombatPort() {
                    @Override public void requireUsableCharacter(CombatActionCommand command) {}
                    @Override public void applyOutcome(CombatActionCommand command, CombatOutcome outcome) {
                        calls.mutation = outcome.mutation();
                    }
                }, command -> 1, command -> {}, new com.dndmaster.adventure.api.AdventureApiConfiguration().aiCombatPort());

        var result = service.resolveCombatAction(commandWithDamage(UUID.randomUUID(), 7, true));

        assertEquals("critical miss (natural 1)", result.judgment());
        assertEquals("RESOLVED", result.resolutionStatus());
        assertTrue(result.outcomeApplied());
        assertTrue(!calls.mutation.hasEffects());
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
                new CharacterCombatPort() {
                    @Override public void requireUsableCharacter(CombatActionCommand command) { calls.character++; }
                    @Override public void applyOutcome(CombatActionCommand command, CombatOutcome outcome) {
                        calls.applied++;
                        calls.mutation = outcome.mutation();
                    }
                },
                command -> { calls.dice++; calls.lastRole = command.role(); return 18; },
                command -> calls.map++,
                ai);
    }

    private static CombatActionCommand command(UUID operationId, CombatActorRole role, String movement) {
        return new CombatActionCommand(
                operationId, AdventureId.generate(), new RuleSetId(UUID.randomUUID()),
                new CharacterSheetId(UUID.randomUUID()), role, "attack", movement);
    }

    private static CombatActionCommand commandWithDamage(UUID operationId, int damageAmount, boolean endCombat) {
        var adventureId = AdventureId.generate();
        return new CombatActionCommand(operationId, adventureId, adventureId.value(),
                new RuleSetId(UUID.randomUUID()), new CharacterSheetId(UUID.randomUUID()), null,
                CombatActorRole.PLAYER, "attack", null, null, null, 0L,
                null, null, null, damageAmount, endCombat);
    }

    private static final class Calls {
        private int character;
        private int dice;
        private int map;
        private int state;
        private int adjudication;
        private int applied;
        private CombatCharacterMutation mutation = CombatCharacterMutation.none();
        private CombatActorRole lastRole;
    }

    private static final class MemoryRepository implements CombatOperationRepository {
        private final Map<UUID, CombatOperation> values = new HashMap<>();
        @Override public Optional<CombatOperation> findById(UUID operationId) { return Optional.ofNullable(values.get(operationId)); }
        @Override public void save(CombatOperation operation) { values.put(operation.id(), operation); }
    }
}
