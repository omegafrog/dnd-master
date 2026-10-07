package com.dndmaster.adventure.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.adventure.application.combat.AiCombatPort;
import com.dndmaster.adventure.application.combat.CharacterCombatPort;
import com.dndmaster.adventure.application.combat.CharacterCombatStatus;
import com.dndmaster.adventure.application.combat.CombatActionCommand;
import com.dndmaster.adventure.application.combat.CombatActionApplicationService;
import com.dndmaster.adventure.application.combat.CombatActionOperation;
import com.dndmaster.adventure.application.combat.CombatActionOperationRepository;
import com.dndmaster.adventure.application.combat.CombatActionResponse;
import com.dndmaster.adventure.application.combat.CombatActorRole;
import com.dndmaster.adventure.application.combat.CombatEncounterRepository;
import com.dndmaster.adventure.application.combat.CombatEventRepository;
import com.dndmaster.adventure.application.combat.CombatNarrationPort;
import com.dndmaster.adventure.application.combat.CombatNarrationPersistenceException;
import com.dndmaster.adventure.application.combat.DiceCombatPort;
import com.dndmaster.adventure.application.combat.RuntimeCombatRejectionException;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.CharacterSheetId;
import com.dndmaster.adventure.domain.adventure.RuleSetId;
import com.dndmaster.adventure.domain.combat.CombatEncounter;
import com.dndmaster.adventure.domain.combat.CombatEvent;
import com.dndmaster.adventure.domain.combat.CombatParticipant;
import com.dndmaster.adventure.domain.combat.CombatStartPolicy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CombatActionPolicyTest {
    private final UUID adventureId = UUID.randomUUID();
    private final UUID heroId = UUID.randomUUID();
    private final UUID goblinId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();
    private final UUID ruleSetId = UUID.randomUUID();
    private final UUID sessionId = UUID.randomUUID();

    @Test
    void rejects_non_current_actor_or_version_without_mutation() {
        Fixture fixture = fixture(command(UUID.randomUUID(), goblinId, 1));

        assertThrows(RuntimeException.class, () -> fixture.service.submit(fixture.command));
        assertEquals(1, fixture.encounters.value.version());
        assertEquals(0, fixture.operations.values.size());
        assertEquals(0, fixture.events.values.size());
        assertEquals(0, fixture.calls.dice);
        assertEquals(0, fixture.calls.character);
    }

    @Test
    void external_failure_does_not_consume_reserved_action() {
        CombatActionCommand command = command(UUID.randomUUID(), heroId, 1);
        Fixture fixture = fixture(command);
        fixture.calls.failCharacter = true;

        assertThrows(RuntimeException.class, () -> fixture.service.submit(command));
        assertEquals(true, fixture.encounters.value.participants().get(0).resources().actionAvailable());
        assertEquals(0, fixture.calls.characterMutations);
        assertEquals(1, fixture.operations.values.size());
        assertEquals(CombatActionOperation.Status.PROCESSING_FAILED,
                fixture.operations.values.get(command.operationId()).status());
    }

    @Test
    void propagates_runtime_character_rejection_instead_of_mapping_it_to_external_failure() {
        CombatActionCommand command = command(UUID.randomUUID(), heroId, 1);
        Fixture fixture = fixture(command);
        fixture.calls.rejectCharacter = true;

        assertThrows(RuntimeCombatRejectionException.class, () -> fixture.service.submit(command));
        assertEquals(CombatActionOperation.Status.PROCESSING_FAILED,
                fixture.operations.values.get(command.operationId()).status());
    }

    @Test
    void retry_resumes_completed_dice_step_without_rerolling_or_double_mutating() {
        CombatActionCommand command = command(UUID.randomUUID(), heroId, 1);
        Fixture fixture = fixture(command);
        fixture.calls.failCharacter = true;
        assertThrows(RuntimeException.class, () -> fixture.service.submit(command));

        fixture.calls.failCharacter = false;
        fixture.service.submit(command);

        assertEquals(1, fixture.calls.dice);
        assertEquals(1, fixture.calls.characterMutations);
    }

    @Test
    void full_success_is_idempotent_and_keeps_human_turn_open() {
        CombatActionCommand command = command(UUID.randomUUID(), heroId, 1);
        Fixture fixture = fixture(command);

        CombatActionResponse first = fixture.service.submit(command);
        CombatActionResponse retry = fixture.service.submit(command);

        assertEquals(first, retry);
        assertEquals(1, fixture.calls.dice);
        assertEquals(1, fixture.calls.characterMutations);
        assertEquals(false, fixture.encounters.value.participants().get(0).resources().actionAvailable());
        assertEquals(heroId, fixture.encounters.value.currentParticipantId());
        assertEquals(1, fixture.events.values.stream().filter(e -> e.eventType().equals("ACTION_RESOLVED")).count());
    }

    @Test
    void enemy_ai_action_uses_encounter_sheet_without_loading_enemy_as_player_character() {
        CombatActionCommand original = command(UUID.randomUUID(), heroId, 1);
        Fixture fixture = fixture(original);
        var statBlock = new com.dndmaster.adventure.domain.combat.CombatEnemyStatBlock(12, 2, 2, "1d4+2",
                new com.dndmaster.adventure.domain.combat.CombatStatBlockSource(UUID.randomUUID(), 1, "rat-source"));
        var enemy = new CombatParticipant(goblinId, "Giant Rat", CombatParticipant.Controller.AI, 15, null,
                com.dndmaster.adventure.domain.combat.TurnResources.initial(), statBlock, 2, "giant-rat");
        fixture.encounters.value = new CombatEncounter(UUID.randomUUID(), adventureId,
                CombatEncounter.Status.ACTIVE, 1, goblinId,
                List.of(new CombatParticipant(heroId, "Hero", CombatParticipant.Controller.PLAYER, 10, "healthy"), enemy),
                1, 0);
        var characterLookups = new java.util.concurrent.atomic.AtomicInteger();
        CharacterCombatPort playerCharactersOnly = new CharacterCombatPort() {
            @Override public void requireUsableCharacter(CombatActionCommand ignored) {
                characterLookups.incrementAndGet();
                throw new IllegalStateException("enemy is not a player character");
            }
            @Override public void applyOutcome(CombatActionCommand ignored,
                    com.dndmaster.adventure.application.combat.CombatOutcome ignoredOutcome) { }
        };
        var service = new CombatActionApplicationService(fixture.encounters, fixture.operations, fixture.events,
                new com.dndmaster.adventure.domain.combat.CombatRulesEngine(), ignored -> 12,
                playerCharactersOnly, ai());
        var action = new CombatActionCommand(UUID.randomUUID(), new AdventureId(adventureId), sessionId,
                new RuleSetId(ruleSetId), new CharacterSheetId(goblinId), null, CombatActorRole.AI,
                "Bite", null, ownerId, goblinId, 1, 10, 2, new CharacterSheetId(heroId), null, false);
        var plan = new com.dndmaster.adventure.application.combat.AiTurnPlan(goblinId,
                new com.dndmaster.adventure.domain.combat.CombatActionIntent(goblinId, "Bite",
                        com.dndmaster.adventure.domain.combat.TurnResourceCost.actionOnly()),
                null, null, heroId, null, false, null, List.of("rat-bite"), null);

        var response = service.submitAi(action, plan);

        assertEquals("COMMITTED", response.status());
        assertEquals(0, characterLookups.get());
        assertTrue(fixture.events.values.stream().anyMatch(event -> event.eventType().equals("ACTION_RESOLVED")));
    }

    @Test
    void companion_ai_attack_fills_attack_values_from_its_character_sheet() {
        UUID companionId = UUID.randomUUID();
        var enemyStats = new com.dndmaster.adventure.domain.combat.CombatEnemyStatBlock(12, 8, 4, "1d4+2",
                new com.dndmaster.adventure.domain.combat.CombatStatBlockSource(UUID.randomUUID(), 1, "rat-source"));
        var companion = new CombatParticipant(companionId, "Companion", CombatParticipant.Controller.AI,
                15, null, com.dndmaster.adventure.domain.combat.TurnResources.initial());
        var enemy = new CombatParticipant(goblinId, "Giant Rat", CombatParticipant.Controller.AI, 10, null,
                com.dndmaster.adventure.domain.combat.TurnResources.initial(), enemyStats, 8, "giant-rat");
        var encounter = new CombatEncounter(UUID.randomUUID(), adventureId, CombatEncounter.Status.ACTIVE, 1,
                companionId, List.of(companion, enemy), 1, 0);
        var encounters = new EncounterStore(encounter);
        var operations = new OperationStore();
        var events = new EventStore();
        var resolved = new java.util.concurrent.atomic.AtomicReference<CombatActionCommand>();
        CharacterCombatPort characters = new CharacterCombatPort() {
            @Override public void requireUsableCharacter(CombatActionCommand ignored) { }
            @Override public Integer attackModifier(CombatActionCommand ignored) { return 5; }
            @Override public Integer damageAmount(CombatActionCommand ignored) { return 4; }
        };
        AiCombatPort aiPort = new AiCombatPort() {
            @Override public void controlState(CombatActionCommand ignored) { }
            @Override public String adjudicate(CombatActionCommand command, int roll) {
                return command.attackModifier() != null && command.targetArmorClass() != null
                        ? "hit (attack=" + (roll + command.attackModifier()) + ", AC=" + command.targetArmorClass() + ")"
                        : "판정 보류";
            }
            @Override public com.dndmaster.adventure.application.combat.CombatOutcome adjudicateOutcome(
                    CombatActionCommand command, int roll) {
                resolved.set(command);
                String judgment = adjudicate(command, roll);
                int damage = judgment.startsWith("hit (") && command.damageAmount() != null
                        ? -command.damageAmount() : 0;
                return new com.dndmaster.adventure.application.combat.CombatOutcome(judgment,
                        new com.dndmaster.adventure.application.combat.CombatCharacterMutation(
                                damage, 0, List.of(), List.of()));
            }
        };
        var service = new CombatActionApplicationService(encounters, operations, events,
                new com.dndmaster.adventure.domain.combat.CombatRulesEngine(), ignored -> 18, characters, aiPort);
        var command = new CombatActionCommand(UUID.randomUUID(), new AdventureId(adventureId), sessionId,
                new RuleSetId(ruleSetId), new CharacterSheetId(companionId), null, CombatActorRole.AI, "attack",
                null, ownerId, companionId, 1, null, null, new CharacterSheetId(goblinId), null, false);
        var plan = new com.dndmaster.adventure.application.combat.AiTurnPlan(companionId,
                new com.dndmaster.adventure.domain.combat.CombatActionIntent(companionId, "attack",
                        com.dndmaster.adventure.domain.combat.TurnResourceCost.actionOnly()),
                null, null, goblinId, null, false, null);

        CombatActionResponse result = service.submitAi(command, plan);

        assertEquals("hit (attack=23, AC=12)", result.judgment());
        assertEquals(5, resolved.get().attackModifier());
        assertEquals(12, resolved.get().targetArmorClass());
        assertEquals(4, resolved.get().damageAmount());
        assertEquals(4, encounters.value.participants().stream()
                .filter(participant -> participant.participantId().equals(goblinId))
                .findFirst().orElseThrow().currentHitPoints());
    }

    @Test
    void enemy_ai_attack_reads_target_armor_class_and_uses_prepared_enemy_numbers() {
        var enemyStats = new com.dndmaster.adventure.domain.combat.CombatEnemyStatBlock(12, 8, 4, "1d4+2",
                new com.dndmaster.adventure.domain.combat.CombatStatBlockSource(UUID.randomUUID(), 1, "rat-source"));
        var enemy = new CombatParticipant(goblinId, "Giant Rat", CombatParticipant.Controller.AI, 15, null,
                com.dndmaster.adventure.domain.combat.TurnResources.initial(), enemyStats, 8, "giant-rat");
        var hero = new CombatParticipant(heroId, "Hero", CombatParticipant.Controller.PLAYER, 10, "healthy");
        var encounters = new EncounterStore(new CombatEncounter(UUID.randomUUID(), adventureId,
                CombatEncounter.Status.ACTIVE, 1, goblinId, List.of(enemy, hero), 1, 0));
        var operations = new OperationStore();
        var events = new EventStore();
        var resolved = new java.util.concurrent.atomic.AtomicReference<CombatActionCommand>();
        var characterMutations = new java.util.concurrent.atomic.AtomicInteger();
        CharacterCombatPort characters = new CharacterCombatPort() {
            @Override public void requireUsableCharacter(CombatActionCommand ignored) { throw new AssertionError(); }
            @Override public Integer armorClass(CombatActionCommand ignored, CharacterSheetId target) { return 15; }
            @Override public void applyOutcome(CombatActionCommand command,
                    com.dndmaster.adventure.application.combat.CombatOutcome outcome) {
                characterMutations.addAndGet(outcome.mutation().hitPointDelta());
            }
        };
        AiCombatPort aiPort = new AiCombatPort() {
            @Override public void controlState(CombatActionCommand ignored) { }
            @Override public String adjudicate(CombatActionCommand command, int roll) {
                return command.attackModifier() != null && command.targetArmorClass() != null
                        ? "hit (attack=" + (roll + command.attackModifier()) + ", AC=" + command.targetArmorClass() + ")"
                        : "판정 보류";
            }
            @Override public com.dndmaster.adventure.application.combat.CombatOutcome adjudicateOutcome(
                    CombatActionCommand command, int roll) {
                resolved.set(command);
                String judgment = adjudicate(command, roll);
                int damage = judgment.startsWith("hit (") && command.damageAmount() != null
                        ? -command.damageAmount() : 0;
                return new com.dndmaster.adventure.application.combat.CombatOutcome(judgment,
                        new com.dndmaster.adventure.application.combat.CombatCharacterMutation(
                                damage, 0, List.of(), List.of()));
            }
        };
        var service = new CombatActionApplicationService(encounters, operations, events,
                new com.dndmaster.adventure.domain.combat.CombatRulesEngine(), ignored -> 12, characters, aiPort);
        var command = new CombatActionCommand(UUID.randomUUID(), new AdventureId(adventureId), sessionId,
                new RuleSetId(ruleSetId), new CharacterSheetId(goblinId), null, CombatActorRole.AI, "bite",
                null, ownerId, goblinId, 1, null, null, new CharacterSheetId(heroId), null, false);
        var plan = new com.dndmaster.adventure.application.combat.AiTurnPlan(goblinId,
                new com.dndmaster.adventure.domain.combat.CombatActionIntent(goblinId, "bite",
                        com.dndmaster.adventure.domain.combat.TurnResourceCost.actionOnly()),
                null, null, heroId, null, false, null);

        CombatActionResponse result = service.submitAi(command, plan);

        assertEquals("hit (attack=16, AC=15)", result.judgment());
        assertEquals(4, resolved.get().attackModifier());
        assertEquals(15, resolved.get().targetArmorClass());
        assertEquals(4, resolved.get().damageAmount());
        assertEquals(-4, characterMutations.get());
    }

    @Test
    void requests_player_narration_once_after_the_canonical_combat_result_is_saved() {
        CombatActionCommand command = command(UUID.randomUUID(), heroId, 1);
        Fixture fixture = fixture(command);
        CombatNarrationPort narration = request -> {
            fixture.calls.narrationCalls++;
            fixture.calls.narrationPrompt = request.command().action() + ":" + request.diceTotal();
            assertEquals(2L, fixture.encounters.value.version());
            return "검이 적을 맞혔습니다.";
        };
        CombatActionApplicationService service = new CombatActionApplicationService(fixture.encounters, fixture.operations,
                fixture.events, new com.dndmaster.adventure.domain.combat.CombatRulesEngine(),
                ignored -> 18, usableCharacter(), ai(), command1 -> {},
                context -> com.dndmaster.adventure.domain.combat.FreeFormActionPlan.narrativeOnly(
                        context.declaration().actorId(), com.dndmaster.adventure.domain.combat.TurnResourceCost.actionOnly(), "", ""),
                adventure -> null, narration);

        CombatActionResponse result = service.submit(command);
        service.submit(command);

        assertEquals("검이 적을 맞혔습니다.", result.narration());
        assertTrue(fixture.calls.narrationPrompt.endsWith(":18"));
        assertEquals(1, fixture.calls.narrationCalls);
        assertEquals(1, fixture.events.values.stream().filter(event -> event.eventType().equals("ACTION_RESOLVED")).count());
        assertEquals(1, fixture.events.values.stream().filter(event -> event.eventType().equals("GM_NARRATION")).count());
    }

    @Test
    void narration_failure_keeps_the_confirmed_combat_result_and_returns_a_korean_result_message() {
        CombatActionCommand command = command(UUID.randomUUID(), heroId, 1);
        Fixture fixture = fixture(command);
        CombatActionApplicationService service = new CombatActionApplicationService(fixture.encounters, fixture.operations,
                fixture.events, new com.dndmaster.adventure.domain.combat.CombatRulesEngine(),
                ignored -> 18, usableCharacter(), ai(), command1 -> {},
                context -> com.dndmaster.adventure.domain.combat.FreeFormActionPlan.narrativeOnly(
                        context.declaration().actorId(), com.dndmaster.adventure.domain.combat.TurnResourceCost.actionOnly(), "", ""),
                adventure -> null, request -> { throw new IllegalStateException("narration unavailable"); });

        CombatActionResponse result = service.submit(command);

        assertEquals("COMMITTED", result.status());
        assertTrue(result.narration() != null && !result.narration().isBlank());
        assertTrue(result.narration().matches(".*[가-힣].*"));
        assertEquals(2L, fixture.encounters.value.version());
        assertEquals(CombatActionOperation.Status.COMMITTED, fixture.operations.values.get(command.operationId()).status());
        assertEquals(1, fixture.events.values.stream().filter(event -> event.eventType().equals("GM_NARRATION")).count());
    }

    @Test
    void narration_for_a_defeated_enemy_uses_the_committed_hit_point_result() {
        CombatActionCommand command = new CombatActionCommand(UUID.randomUUID(), new AdventureId(adventureId), sessionId,
                new RuleSetId(ruleSetId), new CharacterSheetId(heroId), null, CombatActorRole.PLAYER, "attack", null,
                ownerId, heroId, 1, 12, 3, new CharacterSheetId(goblinId), 2, false);
        Fixture fixture = fixture(command);
        var initial = fixture.encounters.value;
        var goblin = new CombatParticipant(goblinId, "거대 쥐", CombatParticipant.Controller.AI, 10, null,
                com.dndmaster.adventure.domain.combat.TurnResources.initial(),
                new com.dndmaster.adventure.domain.combat.CombatEnemyStatBlock(12, 2, 2, "1d4+2",
                        new com.dndmaster.adventure.domain.combat.CombatStatBlockSource(UUID.randomUUID(), 1, "p. 1")));
        fixture.encounters.value = new CombatEncounter(initial.encounterId(), adventureId, CombatEncounter.Status.ACTIVE,
                1, heroId, List.of(initial.participants().getFirst(), goblin), 1, 0);
        AiCombatPort hitForTwoDamage = new AiCombatPort() {
            @Override public void controlState(CombatActionCommand ignored) {}
            @Override public String adjudicate(CombatActionCommand ignored, int diceTotal) { return "hit"; }
            @Override public com.dndmaster.adventure.application.combat.CombatOutcome adjudicateOutcome(
                    CombatActionCommand ignored, int diceTotal) {
                return new com.dndmaster.adventure.application.combat.CombatOutcome("hit",
                        new com.dndmaster.adventure.application.combat.CombatCharacterMutation(-2, 0, List.of(), List.of()));
            }
        };
        CombatActionApplicationService service = new CombatActionApplicationService(fixture.encounters, fixture.operations,
                fixture.events, new com.dndmaster.adventure.domain.combat.CombatRulesEngine(), ignored -> 18,
                usableCharacter(), hitForTwoDamage, ignored -> {},
                context -> com.dndmaster.adventure.domain.combat.FreeFormActionPlan.narrativeOnly(
                        context.declaration().actorId(), com.dndmaster.adventure.domain.combat.TurnResourceCost.actionOnly(),
                        "자유 행동", "자유 행동"), ignored -> new com.dndmaster.adventure.application.combat.CombatEndResult(
                                new com.dndmaster.adventure.domain.combat.PostCombatProjectionPolicy.PostCombatSummary(
                                        adventureId, initial.encounterId(), "ENEMIES_DEFEATED", "전투가 끝났습니다.", false, List.of()), 3),
                ignored -> "거대 쥐는 아직 쓰러지지 않았습니다.");

        CombatActionResponse result = service.submit(command);

        assertTrue(fixture.encounters.value.participants().stream().filter(p -> p.participantId().equals(goblinId))
                .findFirst().orElseThrow().isDefeated());
        assertTrue(result.narration().contains("공격에 쓰러진 대상은 거대 쥐입니다."));
        assertTrue(result.narration().contains("전투가 끝났습니다."));
    }

    @Test
    void confirmed_combat_record_persistence_failure_is_not_reported_as_a_successful_narration() {
        CombatActionCommand command = command(UUID.randomUUID(), heroId, 1);
        Fixture fixture = fixture(command);
        CombatActionApplicationService service = new CombatActionApplicationService(fixture.encounters, fixture.operations,
                fixture.events, new com.dndmaster.adventure.domain.combat.CombatRulesEngine(),
                ignored -> 18, usableCharacter(), ai(), command1 -> {},
                context -> com.dndmaster.adventure.domain.combat.FreeFormActionPlan.narrativeOnly(
                        context.declaration().actorId(), com.dndmaster.adventure.domain.combat.TurnResourceCost.actionOnly(), "", ""),
                adventure -> null, request -> { throw new CombatNarrationPersistenceException(new IllegalStateException("save failed")); });

        assertThrows(RuntimeException.class, () -> service.submit(command));

        assertEquals(2L, fixture.encounters.value.version());
        assertEquals(CombatActionOperation.Status.PROCESSING_FAILED,
                fixture.operations.values.get(command.operationId()).status());
        assertEquals(0, fixture.events.values.stream().filter(event -> event.eventType().equals("GM_NARRATION")).count());
    }

    @Test
    void human_turn_moves_only_after_explicit_end_turn() {
        CombatActionCommand action = command(UUID.randomUUID(), heroId, 1);
        Fixture fixture = fixture(action);
        fixture.service.submit(action);

        fixture.service.endTurn(command(UUID.randomUUID(), heroId, 2));

        assertEquals(goblinId, fixture.encounters.value.currentParticipantId());
        assertEquals(3, fixture.encounters.value.version());
    }

    @Test
    void zero_hit_point_turn_records_death_save_and_advances_turn() {
        CombatActionCommand action = command(UUID.randomUUID(), heroId, 1);
        Fixture fixture = fixture(action);
        fixture.calls.combatStatus = new CharacterCombatStatus(0, 0, 0, false, false);

        CombatActionResponse response = fixture.service.endTurn(command(UUID.randomUUID(), heroId, 1));

        assertEquals("DEATH_SAVE_SUCCESS", response.status());
        assertEquals(18, response.diceTotal());
        assertEquals(1, fixture.calls.deathSaveMutations);
        assertEquals(goblinId, fixture.encounters.value.currentParticipantId());
        assertTrue(fixture.events.values.stream().anyMatch(event -> event.eventType().equals("TURN_ENDED")
                && event.playerPayload().contains("\"successes\":1")));
    }

    private CombatActionCommand command(UUID operationId, UUID actorId, long expectedVersion) {
        return new CombatActionCommand(operationId, new AdventureId(adventureId), sessionId,
                new RuleSetId(ruleSetId), new CharacterSheetId(actorId), null,
                CombatActorRole.PLAYER, "attack", null, ownerId, actorId, expectedVersion,
                null, null, null, null, false);
    }

    private Fixture fixture(CombatActionCommand command) {
        EncounterStore encounters = new EncounterStore(CombatStartPolicy.startFromCommittedGmTurn(true,
                adventureId, List.of(
                        new CombatParticipant(heroId, "Hero", CombatParticipant.Controller.PLAYER, 15, "healthy"),
                        new CombatParticipant(goblinId, "Goblin", CombatParticipant.Controller.AI, 10, null))));
        OperationStore operations = new OperationStore();
        EventStore events = new EventStore();
        Calls calls = new Calls();
        DiceCombatPort dice = ignored -> { calls.dice++; return 18; };
        CharacterCombatPort character = new CharacterCombatPort() {
            @Override public void requireUsableCharacter(CombatActionCommand ignored) {
                calls.character++;
                if (calls.rejectCharacter) {
                    throw new RuntimeCombatRejectionException(RuntimeCombatRejectionException.ZERO_HIT_POINTS_MESSAGE);
                }
            }
            @Override public CharacterCombatStatus combatStatus(CombatActionCommand ignored) { return calls.combatStatus; }
            @Override public void applyDeathSavingThrow(CombatActionCommand ignored, int roll) {
                calls.deathSaveMutations++;
                calls.combatStatus = new CharacterCombatStatus(0, 1, 0, false, false);
            }
            @Override public void applyOutcome(CombatActionCommand ignored, com.dndmaster.adventure.application.combat.CombatOutcome ignoredOutcome) {
                if (calls.failCharacter) throw new IllegalStateException("character unavailable");
                calls.characterMutations++;
            }
        };
        AiCombatPort ai = new AiCombatPort() {
            @Override public void controlState(CombatActionCommand ignored) {}
            @Override public String adjudicate(CombatActionCommand ignored, int ignoredDice) { return "hit"; }
        };
        return new Fixture(new CombatActionApplicationService(encounters, operations, events,
                new com.dndmaster.adventure.domain.combat.CombatRulesEngine(), dice, character, ai),
                encounters, operations, events, calls, command);
    }

    private static CharacterCombatPort usableCharacter() {
        return new CharacterCombatPort() {
            @Override public void requireUsableCharacter(CombatActionCommand ignored) { }
            @Override public void applyOutcome(CombatActionCommand ignored,
                    com.dndmaster.adventure.application.combat.CombatOutcome ignoredOutcome) { }
        };
    }

    private static AiCombatPort ai() {
        return new AiCombatPort() {
            @Override public void controlState(CombatActionCommand ignored) { }
            @Override public String adjudicate(CombatActionCommand ignored, int ignoredDice) { return "hit"; }
        };
    }

    private record Fixture(CombatActionApplicationService service, EncounterStore encounters,
                           OperationStore operations, EventStore events, Calls calls,
                           CombatActionCommand command) {}

    private static final class EncounterStore implements CombatEncounterRepository {
        private CombatEncounter value;
        private EncounterStore(CombatEncounter value) { this.value = value; }
        @Override public Optional<CombatEncounter> findActive(UUID ignored) { return Optional.of(value); }
        @Override public CombatEncounter save(CombatEncounter encounter) { value = encounter; return encounter; }
    }

    private static final class OperationStore implements CombatActionOperationRepository {
        private final Map<UUID, CombatActionOperation> values = new HashMap<>();
        @Override public Optional<CombatActionOperation> findByCommandId(UUID id) { return Optional.ofNullable(values.get(id)); }
        @Override public void save(CombatActionOperation operation) { values.put(operation.commandId(), operation); }
    }

    private static final class EventStore implements CombatEventRepository {
        private final List<CombatEvent> values = new ArrayList<>();
        @Override public void append(CombatEvent event) { values.add(event); }
        @Override public List<CombatEvent> after(UUID ignored, long sequence) { return List.of(); }
    }

    private static final class Calls {
        private int dice;
        private int character;
        private int characterMutations;
        private boolean failCharacter;
        private boolean rejectCharacter;
        private int narrationCalls;
        private CharacterCombatStatus combatStatus;
        private int deathSaveMutations;
        private String narrationPrompt;
    }
}
