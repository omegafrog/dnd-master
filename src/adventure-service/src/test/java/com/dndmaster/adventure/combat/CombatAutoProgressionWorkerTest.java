package com.dndmaster.adventure.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dndmaster.adventure.application.combat.AiCombatDecisionPort;
import com.dndmaster.adventure.application.combat.AiTurnPlan;
import com.dndmaster.adventure.application.combat.CombatActionCommand;
import com.dndmaster.adventure.application.combat.CombatActionResponse;
import com.dndmaster.adventure.application.combat.CombatAutoProgressionWorker;
import com.dndmaster.adventure.application.combat.CombatCommandRejectedException;
import com.dndmaster.adventure.application.combat.CombatTransientFailureException;
import com.dndmaster.adventure.application.combat.CombatWorkItem;
import com.dndmaster.adventure.application.combat.EnemyCharacterSheet;
import com.dndmaster.adventure.application.combat.EnemyCharacterSheetIdentity;
import com.dndmaster.adventure.application.combat.EnemySheetPreparationRequest;
import com.dndmaster.adventure.application.combat.InMemoryEnemyCharacterSheetRepository;
import com.dndmaster.adventure.application.runtime.CombatEnemyAbilityProposal;
import com.dndmaster.adventure.application.combat.InMemoryCombatWorkItemRepository;
import com.dndmaster.adventure.application.combat.CombatEncounterRepository;
import com.dndmaster.adventure.application.session.AdventureAiRequestApplicationService;
import com.dndmaster.adventure.application.session.AdventureSessionRepository;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.CharacterSheetId;
import com.dndmaster.adventure.domain.adventure.RuleSetId;
import com.dndmaster.adventure.domain.combat.CombatEncounter;
import com.dndmaster.adventure.domain.combat.CombatParticipant;
import com.dndmaster.adventure.domain.combat.CombatStartPolicy;
import com.dndmaster.adventure.domain.combat.TurnResourceCost;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;

class CombatAutoProgressionWorkerTest {
    @Test
    void unconfigured_combat_decision_port_does_not_silently_end_a_turn() {
        AiCombatDecisionPort port = context -> null;
        UUID actorId = UUID.randomUUID();
        CombatEncounter encounter = CombatStartPolicy.startFromCommittedGmTurn(true, UUID.randomUUID(), List.of(
                new CombatParticipant(actorId, "Goblin", CombatParticipant.Controller.AI, 20, null)));
        var context = com.dndmaster.adventure.application.combat.AiTacticalInstructionPolicy.contextFor(
                encounter, actorId, "No tactical instruction", List.of());
        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
                () -> port.planTurn(context));
    }

    @Test
    void allows_companion_to_end_turn_after_spending_its_action() {
        UUID adventureId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        CombatEncounter encounter = CombatStartPolicy.startFromCommittedGmTurn(true, adventureId, List.of(
                new CombatParticipant(actorId, "Lin", CombatParticipant.Controller.AI, 12, null,
                        new com.dndmaster.adventure.domain.combat.TurnResources(30, false, true, true))));
        var encounters = new EncounterStore(encounter);
        var workItems = new InMemoryCombatWorkItemRepository();
        var command = new CombatActionCommand(operationId, new AdventureId(adventureId), UUID.randomUUID(),
                new RuleSetId(UUID.randomUUID()), new CharacterSheetId(actorId), null,
                com.dndmaster.adventure.application.combat.CombatActorRole.AI, "AI_TURN", null,
                UUID.randomUUID(), actorId, encounter.version(), null, null, null, null, false);
        workItems.enqueue(new CombatWorkItem(UUID.randomUUID(), encounter.encounterId(), operationId,
                encounter.version(), CombatWorkItem.WorkType.AI_TURN, Instant.parse("2026-01-01T00:00:00Z"), 0,
                new com.dndmaster.adventure.application.combat.AiTacticalInstructionContext("Protect the healer"), command));
        AtomicInteger turnEnds = new AtomicInteger();
        AiCombatDecisionPort decision = new AiCombatDecisionPort() {
            @Override public com.dndmaster.adventure.domain.combat.FreeFormActionPlan interpretFreeForm(
                    com.dndmaster.adventure.application.combat.FreeFormCombatContext ignored) {
                throw new UnsupportedOperationException();
            }
            @Override public AiTurnPlan planTurn(com.dndmaster.adventure.application.combat.AiCombatTurnContext ignored) {
                return new AiTurnPlan(actorId,
                        new com.dndmaster.adventure.domain.combat.CombatActionIntent(actorId, "ATTACK", TurnResourceCost.actionOnly()),
                        null, null, null, null, false, null, List.of("rulebook-page-123"), null);
            }
        };
        var worker = new CombatAutoProgressionWorker("worker", workItems, encounters, decision,
                (ignoredCommand, ignoredPlan) -> { throw new AssertionError("spent companion should end its turn"); },
                turnEndCommand -> {
                    assertEquals("END_TURN", turnEndCommand.action());
                    turnEnds.incrementAndGet();
                    return new com.dndmaster.adventure.application.combat.CombatActionResponse(
                            encounter.encounterId(), operationId, encounter.version() + 1,
                            "TURN_ENDED", null, null, List.of());
                }, 10);

        worker.processOnce(Instant.parse("2026-01-01T00:00:00Z"));

        assertEquals(1, turnEnds.get());
        assertEquals(CombatWorkItem.Status.COMPLETED, workItems.findByOperationId(operationId).orElseThrow().status());
    }

    @Test
    void enemy_preparation_work_activates_only_after_profile_is_present_and_initializes_combat_state() {
        UUID adventureId = UUID.randomUUID();
        UUID enemyId = UUID.randomUUID();
        var identity = new EnemyCharacterSheetIdentity(adventureId, UUID.randomUUID(), 1, UUID.randomUUID(),
                List.of(UUID.randomUUID()), "goblin");
        var preparing = CombatStartPolicy.prepareFromCommittedGmTurn(true, adventureId, List.of(
                new CombatParticipant(enemyId, "Goblin", CombatParticipant.Controller.AI, 20, "enemy",
                        com.dndmaster.adventure.domain.combat.TurnResources.initial(), null, null, "goblin")));
        var encounters = new EncounterStore(preparing);
        var workItems = new InMemoryCombatWorkItemRepository();
        var sheets = new InMemoryEnemyCharacterSheetRepository();
        var request = new EnemySheetPreparationRequest(UUID.randomUUID(), adventureId,
                List.of(new EnemySheetPreparationRequest.Enemy(identity,
                        new com.dndmaster.adventure.application.runtime.CombatEnemyProposal("scene", "goblin", "Goblin", 1))));
        UUID workId = UUID.randomUUID();
        var work = CombatWorkItem.enemySheetPreparation(workId, preparing.encounterId(), preparing.version(),
                Instant.parse("2026-01-01T00:00:00Z"), request);
        workItems.enqueue(work);
        var worker = new CombatAutoProgressionWorker("worker", workItems, encounters, decisions(enemyId),
                (ignoredCommand, ignoredPlan) -> null, ignoredCommand -> null, 10, null, null, sheets);

        worker.processOnce(Instant.parse("2026-01-01T00:00:00Z"));
        assertEquals(CombatEncounter.Status.PREPARING, encounters.value.status());
        assertEquals(CombatWorkItem.Status.PENDING, workItems.get(workId).status());

        var abilities = List.of("STR", "DEX", "CON", "INT", "WIS", "CHA").stream()
                .map(name -> new CombatEnemyAbilityProposal(name, 10, List.of("goblin-rule"))).toList();
        sheets.saveIfAbsent(new EnemyCharacterSheet(identity, "Goblin",
                new com.dndmaster.adventure.domain.combat.CombatEnemyStatBlock(12, 7, 2, "1d6",
                        new com.dndmaster.adventure.domain.combat.CombatStatBlockSource(UUID.randomUUID(), 1, "goblin-rule")),
                abilities, List.of(new EnemyCharacterSheet.EnemyCombatAction("Scimitar", "Melee attack", List.of("goblin-rule")))));
        worker.processOnce(Instant.parse("2026-01-01T00:00:03Z"));

        assertEquals(CombatEncounter.Status.ACTIVE, encounters.value.status());
        var goblin = encounters.value.participants().getFirst();
        assertEquals(7, goblin.currentHitPoints());
        assertEquals(CombatWorkItem.Status.COMPLETED, workItems.get(workId).status());
    }

    @Test
    void releases_the_initial_request_after_a_successful_terminal_ai_follow_up() {
        UUID adventureId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        CombatEncounter encounter = CombatStartPolicy.startFromCommittedGmTurn(true, adventureId, List.of(
                new CombatParticipant(actorId, "Goblin", CombatParticipant.Controller.AI, 20, null)));
        var workItems = new InMemoryCombatWorkItemRepository();
        var command = new CombatActionCommand(operationId, new AdventureId(adventureId), sessionId,
                new RuleSetId(UUID.randomUUID()), new CharacterSheetId(actorId), null,
                com.dndmaster.adventure.application.combat.CombatActorRole.AI, "AI_TURN", null,
                ownerId, actorId, encounter.version(), null, null, null, null, false);
        workItems.enqueue(new CombatWorkItem(UUID.randomUUID(), encounter.encounterId(), operationId,
                encounter.version(), CombatWorkItem.WorkType.AI_TURN, Instant.parse("2026-01-01T00:00:00Z"), 0,
                new com.dndmaster.adventure.application.combat.AiTacticalInstructionContext("Protect the healer"), command,
                0, requestId));
        AdventureSessionRepository sessions = mock(AdventureSessionRepository.class);
        when(sessions.releaseAiRequest(new com.dndmaster.adventure.domain.adventure.SessionId(sessionId),
                new com.dndmaster.adventure.domain.adventure.OwnerPlayerId(ownerId), requestId)).thenReturn(true);
        var worker = new CombatAutoProgressionWorker("worker-1", workItems, new EncounterStore(encounter), decisions(actorId),
                (ignoredCommand, ignoredPlan) -> null, ignoredCommand -> null, 10, null,
                new AdventureAiRequestApplicationService(sessions));

        worker.processOnce(Instant.parse("2026-01-01T00:00:00Z"));

        verify(sessions).releaseAiRequest(new com.dndmaster.adventure.domain.adventure.SessionId(sessionId),
                new com.dndmaster.adventure.domain.adventure.OwnerPlayerId(ownerId), requestId);
    }

    @Test
    void retries_ai_turn_decision_failures_and_keeps_the_turn_request_open() {
        UUID adventureId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        CombatEncounter encounter = CombatStartPolicy.startFromCommittedGmTurn(true, adventureId, List.of(
                new CombatParticipant(actorId, "Goblin", CombatParticipant.Controller.AI, 20, null)));
        var encounters = new EncounterStore(encounter);
        var workItems = new InMemoryCombatWorkItemRepository();
        var command = new CombatActionCommand(operationId, new AdventureId(adventureId), sessionId,
                new RuleSetId(UUID.randomUUID()), new CharacterSheetId(actorId), null,
                com.dndmaster.adventure.application.combat.CombatActorRole.AI, "AI_TURN", null,
                ownerId, actorId, encounter.version(), null, null, null, null, false);
        workItems.enqueue(new CombatWorkItem(UUID.randomUUID(), encounter.encounterId(), operationId,
                encounter.version(), CombatWorkItem.WorkType.AI_TURN, Instant.parse("2026-01-01T00:00:00Z"), 0,
                new com.dndmaster.adventure.application.combat.AiTacticalInstructionContext("Protect the healer"), command,
                0, requestId));
        AtomicInteger proposals = new AtomicInteger();
        AiCombatDecisionPort failingDecision = new AiCombatDecisionPort() {
            @Override public com.dndmaster.adventure.domain.combat.FreeFormActionPlan interpretFreeForm(
                    com.dndmaster.adventure.application.combat.FreeFormCombatContext ignored) {
                throw new UnsupportedOperationException();
            }
            @Override public AiTurnPlan planTurn(com.dndmaster.adventure.application.combat.AiCombatTurnContext context) {
                proposals.incrementAndGet();
                throw new CombatTransientFailureException("AI unavailable");
            }
        };
        AdventureSessionRepository sessions = mock(AdventureSessionRepository.class);
        var worker = new CombatAutoProgressionWorker("worker-1", workItems, encounters, failingDecision,
                (ignoredCommand, ignoredPlan) -> { throw new AssertionError("failed proposal must not execute"); },
                ignoredCommand -> { throw new AssertionError("turn must not be skipped"); }, 10, null,
                new AdventureAiRequestApplicationService(sessions));

        Instant first = Instant.parse("2026-01-01T00:00:00Z");
        worker.processOnce(first);

        CombatWorkItem retry = workItems.findByOperationId(operationId).orElseThrow();
        assertEquals(CombatWorkItem.Status.PENDING, retry.status());
        assertEquals(1, retry.attemptCount());
        assertEquals(first.plusSeconds(2), retry.dueAt());
        assertEquals(1, proposals.get());
        org.mockito.Mockito.verifyNoInteractions(sessions);
    }

    @Test
    void retries_action_application_failures_without_advancing_encounter() {
        UUID adventureId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        CombatEncounter encounter = CombatStartPolicy.startFromCommittedGmTurn(true, adventureId, List.of(
                new CombatParticipant(actorId, "Goblin", CombatParticipant.Controller.AI, 20, null)));
        var encounters = new EncounterStore(encounter);
        var workItems = new InMemoryCombatWorkItemRepository();
        var command = new CombatActionCommand(operationId, new AdventureId(adventureId), UUID.randomUUID(),
                new RuleSetId(UUID.randomUUID()), new CharacterSheetId(actorId), null,
                com.dndmaster.adventure.application.combat.CombatActorRole.AI, "AI_TURN", null,
                null, actorId, encounter.version(), null, null, null, null, false);
        workItems.enqueue(new CombatWorkItem(UUID.randomUUID(), encounter.encounterId(), operationId,
                encounter.version(), CombatWorkItem.WorkType.AI_TURN, Instant.parse("2026-01-01T00:00:00Z"), 0,
                new com.dndmaster.adventure.application.combat.AiTacticalInstructionContext("Protect the healer"), command));
        AtomicInteger calls = new AtomicInteger();
        AtomicInteger proposalCalls = new AtomicInteger();
        AiCombatDecisionPort delegate = decisions(actorId);
        AiCombatDecisionPort decisions = new AiCombatDecisionPort() {
            @Override public com.dndmaster.adventure.domain.combat.FreeFormActionPlan interpretFreeForm(
                    com.dndmaster.adventure.application.combat.FreeFormCombatContext context) {
                return delegate.interpretFreeForm(context);
            }
            @Override public AiTurnPlan planTurn(com.dndmaster.adventure.application.combat.AiCombatTurnContext context) {
                proposalCalls.incrementAndGet();
                return delegate.planTurn(context);
            }
        };

        var worker = new CombatAutoProgressionWorker("worker-1", workItems, encounters, decisions,
                (ignoredCommand, ignoredPlan) -> {
                    calls.incrementAndGet();
                    throw new CombatTransientFailureException("AI unavailable");
                }, ignoredCommand -> { throw new AssertionError("turn must not be skipped"); },
                10);

        Instant first = Instant.parse("2026-01-01T00:00:00Z");
        worker.processOnce(first);

        CombatWorkItem retry = workItems.findByOperationId(operationId).orElseThrow();
        assertEquals(CombatWorkItem.Status.PENDING, retry.status());
        assertEquals(1, retry.attemptCount());
        assertEquals(1, calls.get());
        assertEquals("attack", retry.command().action());
        assertEquals(encounter.version(), encounters.value.version());

        worker.processOnce(first.plusSeconds(2));
        assertEquals(2, calls.get());
        assertEquals(1, proposalCalls.get());
        assertEquals(CombatWorkItem.Status.PENDING, workItems.findByOperationId(operationId).orElseThrow().status());
        assertEquals(encounter.version(), encounters.value.version());
    }

    @Test
    void marks_rejected_ai_action_failed_and_releases_request_instead_of_retrying_forever() {
        UUID adventureId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        CombatEncounter encounter = CombatStartPolicy.startFromCommittedGmTurn(true, adventureId, List.of(
                new CombatParticipant(actorId, "Goblin", CombatParticipant.Controller.AI, 20, null)));
        var encounters = new EncounterStore(encounter);
        var workItems = new InMemoryCombatWorkItemRepository();
        var command = new CombatActionCommand(operationId, new AdventureId(adventureId), sessionId,
                new RuleSetId(UUID.randomUUID()), new CharacterSheetId(actorId), null,
                com.dndmaster.adventure.application.combat.CombatActorRole.AI, "AI_TURN", null,
                ownerId, actorId, encounter.version(), null, null, null, null, false);
        workItems.enqueue(new CombatWorkItem(UUID.randomUUID(), encounter.encounterId(), operationId,
                encounter.version(), CombatWorkItem.WorkType.AI_TURN, Instant.parse("2026-01-01T00:00:00Z"), 0,
                new com.dndmaster.adventure.application.combat.AiTacticalInstructionContext("Protect the healer"), command,
                0, requestId));
        AdventureSessionRepository sessions = mock(AdventureSessionRepository.class);
        when(sessions.releaseAiRequest(new com.dndmaster.adventure.domain.adventure.SessionId(sessionId),
                new com.dndmaster.adventure.domain.adventure.OwnerPlayerId(ownerId), requestId)).thenReturn(true);
        var worker = new CombatAutoProgressionWorker("worker-1", workItems, encounters, decisions(actorId),
                (ignoredCommand, ignoredPlan) -> {
                    throw new CombatCommandRejectedException("ACTION_NOT_ALLOWED", List.of("ACTION_ALREADY_SPENT"));
                }, ignoredCommand -> { throw new AssertionError("rejected action must not end the turn"); }, 10,
                null, new AdventureAiRequestApplicationService(sessions));

        worker.processOnce(Instant.parse("2026-01-01T00:00:00Z"));

        CombatWorkItem failed = workItems.findByOperationId(operationId).orElseThrow();
        assertEquals(CombatWorkItem.Status.FAILED, failed.status());
        assertEquals("ACTION_NOT_ALLOWED", failed.failure());
        assertEquals(1, failed.attemptCount());
        verify(sessions).releaseAiRequest(new com.dndmaster.adventure.domain.adventure.SessionId(sessionId),
                new com.dndmaster.adventure.domain.adventure.OwnerPlayerId(ownerId), requestId);
    }

    private static AiCombatDecisionPort decisions(UUID actorId) {
        return new AiCombatDecisionPort() {
            @Override public com.dndmaster.adventure.domain.combat.FreeFormActionPlan interpretFreeForm(
                    com.dndmaster.adventure.application.combat.FreeFormCombatContext ignored) {
                return com.dndmaster.adventure.domain.combat.FreeFormActionPlan.narrativeOnly(
                        actorId, TurnResourceCost.actionOnly(), "ignored", "ignored");
            }

            @Override public AiTurnPlan planTurn(com.dndmaster.adventure.application.combat.AiCombatTurnContext context) {
                assertEquals("Protect the healer", context.tacticalInstruction().instruction());
                return AiTurnPlan.action(actorId, "attack", TurnResourceCost.actionOnly());
            }
        };
    }

    private static final class EncounterStore implements CombatEncounterRepository {
        private CombatEncounter value;
        private EncounterStore(CombatEncounter value) { this.value = value; }
        @Override public Optional<CombatEncounter> findActive(UUID ignored) { return Optional.of(value); }
        @Override public Optional<CombatEncounter> findByEncounterId(UUID ignored) { return Optional.of(value); }
        @Override public CombatEncounter save(CombatEncounter encounter) { value = encounter; return encounter; }
    }
}
