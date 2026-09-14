package com.dndmaster.adventure.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dndmaster.adventure.application.combat.AiCombatDecisionPort;
import com.dndmaster.adventure.application.combat.AiTurnPlan;
import com.dndmaster.adventure.application.combat.CombatActionCommand;
import com.dndmaster.adventure.application.combat.CombatActionResponse;
import com.dndmaster.adventure.application.combat.CombatAutoProgressionWorker;
import com.dndmaster.adventure.application.combat.CombatTransientFailureException;
import com.dndmaster.adventure.application.combat.CombatWorkItem;
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
    void releases_the_initial_request_after_the_first_ai_follow_up_failure_without_automatic_retry() {
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
                (ignoredCommand, ignoredPlan) -> { throw new CombatTransientFailureException("AI unavailable"); },
                ignoredCommand -> { throw new AssertionError("turn must not be skipped"); }, 10, null,
                new AdventureAiRequestApplicationService(sessions));

        Instant first = Instant.parse("2026-01-01T00:00:00Z");
        worker.processOnce(first);

        verify(sessions).releaseAiRequest(new com.dndmaster.adventure.domain.adventure.SessionId(sessionId),
                new com.dndmaster.adventure.domain.adventure.OwnerPlayerId(ownerId), requestId);
        assertEquals(CombatWorkItem.Status.FAILED, workItems.findByOperationId(operationId).orElseThrow().status());
        assertEquals(1, workItems.findByOperationId(operationId).orElseThrow().attemptCount());
    }

    @Test
    void surfaces_ai_follow_up_failure_without_automatic_retry_or_advancing_encounter() {
        UUID adventureId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        CombatEncounter encounter = CombatStartPolicy.startFromCommittedGmTurn(true, adventureId, List.of(
                new CombatParticipant(actorId, "Goblin", CombatParticipant.Controller.AI, 20, null)));
        var encounters = new EncounterStore(encounter);
        var workItems = new InMemoryCombatWorkItemRepository();
        var command = new CombatActionCommand(operationId, new AdventureId(adventureId), UUID.randomUUID(),
                new RuleSetId(UUID.randomUUID()), new CharacterSheetId(actorId), null,
                com.dndmaster.adventure.application.combat.CombatActorRole.AI, "attack", null,
                null, actorId, encounter.version(), null, null, null, null, false);
        workItems.enqueue(new CombatWorkItem(UUID.randomUUID(), encounter.encounterId(), operationId,
                encounter.version(), CombatWorkItem.WorkType.AI_TURN, Instant.parse("2026-01-01T00:00:00Z"), 0,
                new com.dndmaster.adventure.application.combat.AiTacticalInstructionContext("Protect the healer"), command));
        AtomicInteger calls = new AtomicInteger();
        AiCombatDecisionPort decisions = decisions(actorId);

        var worker = new CombatAutoProgressionWorker("worker-1", workItems, encounters, decisions,
                (ignoredCommand, ignoredPlan) -> {
                    calls.incrementAndGet();
                    throw new CombatTransientFailureException("AI unavailable");
                }, ignoredCommand -> { throw new AssertionError("turn must not be skipped"); },
                10);

        Instant first = Instant.parse("2026-01-01T00:00:00Z");
        worker.processOnce(first);

        CombatWorkItem failed = workItems.findByOperationId(operationId).orElseThrow();
        assertEquals(CombatWorkItem.Status.FAILED, failed.status());
        assertEquals(1, failed.attemptCount());
        assertEquals(1, calls.get());
        assertEquals(encounter.version(), encounters.value.version());
        assertSame(command, failed.command());
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
