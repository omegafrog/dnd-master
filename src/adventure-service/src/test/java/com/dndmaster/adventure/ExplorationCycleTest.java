package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.*;

import com.dndmaster.adventure.domain.runtime.ExplorationCycle;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ExplorationCycleTest {
    @Test void groupTurnsPauseWorldTimeAndThenAdvanceThroughSixSecondEnvironmentPhases() {
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        ExplorationCycle cycle = ExplorationCycle.start(UUID.randomUUID(), Map.of(first, 18, second, 7));

        assertEquals(0, cycle.elapsedSeconds());
        assertEquals(first, cycle.currentGroupId());
        cycle = cycle.finishGroupTurn(first, false);
        assertEquals(second, cycle.currentGroupId());
        assertEquals(0, cycle.elapsedSeconds());
        cycle = cycle.finishGroupTurn(second, false);
        assertEquals(ExplorationCycle.Phase.ENVIRONMENT, cycle.phase());

        UUID enemyPhase = cycle.currentPhaseCommandId();
        cycle = cycle.completeEnvironmentPhase(enemyPhase);
        assertEquals(ExplorationCycle.EnvironmentPhase.HAZARDS_AND_ENVIRONMENT, cycle.environmentPhase());
        cycle = cycle.completeEnvironmentPhase(cycle.currentPhaseCommandId());
        cycle = cycle.completeEnvironmentPhase(cycle.currentPhaseCommandId());
        assertEquals(6, cycle.elapsedSeconds());
        assertEquals(ExplorationCycle.Phase.GROUP_TURNS, cycle.phase());
        assertEquals(first, cycle.currentGroupId());
    }

    @Test void interruptionEndsCurrentGroupTurnAndResumesAtNextUnfinishedPhase() {
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        ExplorationCycle cycle = ExplorationCycle.start(UUID.randomUUID(), Map.of(first, 12, second, 10))
                .finishGroupTurn(first, true);

        assertEquals(second, cycle.currentGroupId());
        assertTrue(cycle.groupTurnFinished(first));
    }

    @Test void mapStopEventPausesAllGroupsUntilResolutionThenResumesAtNextInitiative() {
        UUID first = UUID.randomUUID(), second = UUID.randomUUID(), operation = UUID.randomUUID();
        ExplorationCycle cycle = ExplorationCycle.start(UUID.randomUUID(), Map.of(first, 12, second, 10));
        UUID active = cycle.currentGroupId();
        UUID next = cycle.groupOrder().stream().filter(group -> !group.equals(active)).findFirst().orElseThrow();
        var event = new ExplorationCycle.MovementStopEvent(UUID.randomUUID(), active, operation,
                "SPATIAL_FEATURE_DISCOVERED", java.util.List.of("TRAP_DISCOVERED:2,1"),
                new ExplorationCycle.Position(2, 1), null);

        cycle = cycle.interruptGroupTurn(active, event);
        ExplorationCycle paused = cycle;
        assertNull(cycle.currentGroupId());
        assertSame(cycle, cycle.interruptGroupTurn(active, event));
        assertThrows(IllegalStateException.class, () -> paused.finishGroupTurn(next, false));

        cycle = cycle.resolveMovementEvent(event.id());
        assertEquals(next, cycle.currentGroupId());
        assertSame(cycle, cycle.resolveMovementEvent(event.id()));
        assertTrue(cycle.groupTurnFinished(active));
    }

    @Test void linkedCombatPausesMovementCycleAndMatchingEndedEncounterResumesIt() {
        UUID first = UUID.randomUUID(), second = UUID.randomUUID(), encounterId = UUID.randomUUID();
        ExplorationCycle cycle = ExplorationCycle.start(UUID.randomUUID(), Map.of(first, 12, second, 10));
        UUID interruptedGroup = cycle.currentGroupId();
        UUID nextGroup = cycle.groupOrder().stream().filter(id -> !id.equals(interruptedGroup)).findFirst().orElseThrow();
        var event = new ExplorationCycle.MovementStopEvent(UUID.randomUUID(), interruptedGroup, UUID.randomUUID(),
                "HOSTILE_OBSERVED", java.util.List.of("HOSTILE_OBSERVED"), new ExplorationCycle.Position(2, 1), UUID.randomUUID());

        cycle = cycle.interruptGroupTurn(interruptedGroup, event).linkCombatEncounter(encounterId);
        ExplorationCycle suspended = cycle;
        assertNull(cycle.currentGroupId());
        assertEquals(encounterId, cycle.linkedCombatEncounterId());
        assertThrows(IllegalStateException.class, () -> suspended.resolveMovementEvent(event.id()));
        assertThrows(IllegalStateException.class, () -> suspended.finishGroupTurn(nextGroup, false));

        cycle = cycle.resumeAfterCombat(encounterId);
        assertNull(cycle.linkedCombatEncounterId());
        assertNull(cycle.currentGroupId());
        cycle = cycle.resolveMovementEvent(event.id());
        assertEquals(nextGroup, cycle.currentGroupId());
        assertThrows(IllegalArgumentException.class, () -> suspended.resumeAfterCombat(UUID.randomUUID()));
    }

    @Test void completedEnvironmentPhaseIsIdempotentAndUnknownGroupCannotFinishTurn() {
        UUID group = UUID.randomUUID();
        ExplorationCycle initial = ExplorationCycle.start(UUID.randomUUID(), Map.of(group, 12));
        assertThrows(IllegalArgumentException.class, () -> initial.finishGroupTurn(UUID.randomUUID(), false));
        ExplorationCycle cycle = initial.finishGroupTurn(group, false);
        UUID commandId = cycle.currentPhaseCommandId();
        ExplorationCycle advanced = cycle.completeEnvironmentPhase(commandId);
        assertSame(advanced, advanced.completeEnvironmentPhase(commandId));
    }

    @Test void tiedInitiativesAreRandomizedOnceAndSavedInCycleOrder() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        Map<UUID, Integer> scores = new LinkedHashMap<>();
        scores.put(a, 10);
        scores.put(b, 10);
        var deterministicRandom = new java.util.Random() {
            @Override public int nextInt(int bound) { return 0; }
        };
        var order = com.dndmaster.adventure.domain.runtime.ExplorationInitiativePolicy.order(scores, deterministicRandom);
        assertEquals(order, ExplorationCycle.start(UUID.randomUUID(), scores, deterministicRandom).groupOrder());
    }

    @Test void environmentProposalAndAppliedActionsSurviveRetryWithoutBeingReplaced() {
        UUID group = UUID.randomUUID();
        ExplorationCycle cycle = ExplorationCycle.start(UUID.randomUUID(), Map.of(group, 12)).finishGroupTurn(group, false);
        var proposal = new ExplorationCycle.EnvironmentProposal("순찰병이 복도를 따라 이동합니다.", java.util.List.of(
                new ExplorationCycle.EnemyMove(UUID.randomUUID(), java.util.List.of(
                        new com.dndmaster.adventure.domain.runtime.ExplorationCycle.Position(2, 1)))));
        cycle = cycle.withEnvironmentProposal(proposal);
        UUID actionId = UUID.randomUUID();
        cycle = cycle.completeEnvironmentAction(actionId);
        ExplorationCycle persisted = cycle;

        assertSame(cycle, cycle.completeEnvironmentAction(actionId));
        assertEquals(proposal, cycle.pendingEnvironmentProposal());
        assertThrows(IllegalStateException.class, () -> persisted.withEnvironmentProposal(
                new ExplorationCycle.EnvironmentProposal("replacement", java.util.List.of())));
    }

    @Test void encounterRequestSuspendsEnvironmentUntilCommittedCombatIsLinked() {
        UUID group = UUID.randomUUID(), encounter = UUID.randomUUID();
        ExplorationCycle cycle = ExplorationCycle.start(UUID.randomUUID(), Map.of(group, 12)).finishGroupTurn(group, false);
        UUID phaseId = cycle.currentPhaseCommandId();
        ExplorationCycle requested = cycle.withEnvironmentProposal(new ExplorationCycle.EnvironmentProposal(
                "적대적 접촉이 발생했습니다.", java.util.List.of(), 0, true, null));

        assertThrows(IllegalStateException.class, () -> requested.completeEnvironmentPhase(phaseId));
        ExplorationCycle handoff = requested.linkCombatEncounter(encounter);
        assertFalse(handoff.pendingEnvironmentProposal().encounterRequested());
        assertEquals(encounter, handoff.pendingEnvironmentProposal().linkedCombatEncounterId());
        assertEquals(phaseId, handoff.currentPhaseCommandId());
        assertEquals(0, handoff.elapsedSeconds());
    }

    @Test void enemyRoutesAreRejectedOutsideTheEnemyAndPatrolPhase() {
        UUID group = UUID.randomUUID();
        ExplorationCycle cycle = ExplorationCycle.start(UUID.randomUUID(), Map.of(group, 12))
                .finishGroupTurn(group, false);
        var enemyMove = new ExplorationCycle.EnemyMove(UUID.randomUUID(), java.util.List.of(
                new ExplorationCycle.Position(1, 1), new ExplorationCycle.Position(2, 1)));
        assertEquals(ExplorationCycle.EnvironmentPhase.ENEMIES_PATROLS_AND_DETECTION, cycle.environmentPhase());
        ExplorationCycle enemyPhase = cycle;
        assertDoesNotThrow(() -> enemyPhase.withEnvironmentProposal(
                new ExplorationCycle.EnvironmentProposal("valid phase", java.util.List.of(enemyMove))));

        cycle = cycle.completeEnvironmentPhase(cycle.currentPhaseCommandId());

        assertEquals(ExplorationCycle.EnvironmentPhase.HAZARDS_AND_ENVIRONMENT, cycle.environmentPhase());
        ExplorationCycle hazardPhase = cycle;
        assertThrows(IllegalArgumentException.class, () -> hazardPhase.withEnvironmentProposal(
                new ExplorationCycle.EnvironmentProposal("wrong phase", java.util.List.of(enemyMove))));
        cycle = cycle.completeEnvironmentPhase(cycle.currentPhaseCommandId());
        ExplorationCycle durationPhase = cycle;
        assertEquals(ExplorationCycle.EnvironmentPhase.DURATIONS_AND_DEADLINES, cycle.environmentPhase());
        assertThrows(IllegalArgumentException.class, () -> durationPhase.withEnvironmentProposal(
                new ExplorationCycle.EnvironmentProposal("wrong phase", java.util.List.of(enemyMove))));
    }

    @Test void spatialFeatureInteractionsAreAcceptedOnlyDuringHazardsAndEnvironmentPhase() {
        UUID group = UUID.randomUUID(), featureId = UUID.randomUUID();
        ExplorationCycle cycle = ExplorationCycle.start(UUID.randomUUID(), Map.of(group, 12)).finishGroupTurn(group, false);
        var interaction = java.util.List.of(new ExplorationCycle.FeatureInteraction(featureId));

        ExplorationCycle enemyPhase = cycle;
        assertThrows(IllegalArgumentException.class, () -> enemyPhase.withEnvironmentProposal(
                new ExplorationCycle.EnvironmentProposal("wrong phase", java.util.List.of(), interaction, -1, false, null)));
        cycle = cycle.completeEnvironmentPhase(cycle.currentPhaseCommandId());
        assertEquals(ExplorationCycle.EnvironmentPhase.HAZARDS_AND_ENVIRONMENT, cycle.environmentPhase());
        ExplorationCycle hazards = cycle;
        assertDoesNotThrow(() -> hazards.withEnvironmentProposal(
                new ExplorationCycle.EnvironmentProposal("allowed phase", java.util.List.of(), interaction, -1, false, null)));
        cycle = cycle.completeEnvironmentPhase(cycle.currentPhaseCommandId());
        ExplorationCycle durations = cycle;
        assertThrows(IllegalArgumentException.class, () -> durations.withEnvironmentProposal(
                new ExplorationCycle.EnvironmentProposal("wrong phase", java.util.List.of(), interaction, -1, false, null)));
    }

    @Test void resolvingPendingEncounterWithoutCombatKeepsPhaseAndAppliedActionsReadyToResume() {
        UUID group = UUID.randomUUID(), enemy = UUID.randomUUID(), appliedAction = UUID.randomUUID();
        ExplorationCycle cycle = ExplorationCycle.start(UUID.randomUUID(), Map.of(group, 12)).finishGroupTurn(group, false)
                .withEnvironmentProposal(new ExplorationCycle.EnvironmentProposal("적이 물러납니다.",
                        java.util.List.of(new ExplorationCycle.EnemyMove(enemy,
                                java.util.List.of(new ExplorationCycle.Position(1, 1)))), 7, true, null))
                .completeEnvironmentAction(appliedAction);
        UUID phaseId = cycle.currentPhaseCommandId();

        ExplorationCycle resolved = cycle.resolveEncounterWithoutCombat();

        assertEquals("적이 물러납니다.", resolved.pendingEnvironmentProposal().narration());
        assertFalse(resolved.pendingEnvironmentProposal().encounterRequested());
        assertEquals(java.util.List.of(appliedAction), resolved.completedEnvironmentActions().stream().toList());
        assertEquals(phaseId, resolved.currentPhaseCommandId());
        assertEquals(0, resolved.elapsedSeconds());
        assertEquals(ExplorationCycle.EnvironmentPhase.ENEMIES_PATROLS_AND_DETECTION, resolved.environmentPhase());
        assertEquals(ExplorationCycle.EnvironmentPhase.HAZARDS_AND_ENVIRONMENT,
                resolved.completeEnvironmentPhase(phaseId).environmentPhase());
    }

    @Test void threatDiscoveryStaysWithDiscovererUntilExplicitGroupNotification() {
        UUID first = UUID.randomUUID(), second = UUID.randomUUID(), groupId = UUID.randomUUID();
        var event = new ExplorationCycle.MovementStopEvent(UUID.randomUUID(), groupId, UUID.randomUUID(),
                "HOSTILE_OBSERVED", java.util.List.of("HOSTILE_OBSERVED"), new ExplorationCycle.Position(1, 1), null,
                java.util.Set.of(first), java.util.Set.of(first), false);
        var cycle = ExplorationCycle.start(UUID.randomUUID(), Map.of(groupId, 10));
        cycle = cycle.interruptGroupTurn(groupId, event);

        assertEquals(java.util.Set.of(first), cycle.pendingMovementEvent().awareCharacterSheetIds());
        var shared = cycle.shareThreatWithGroup(groupId, first, java.util.Set.of(first, second), false);
        assertEquals(java.util.Set.of(first, second), shared.pendingMovementEvent().awareCharacterSheetIds());
        assertSame(shared, shared.shareThreatWithGroup(groupId, first, java.util.Set.of(first, second), false));
        ExplorationCycle persisted = cycle;
        assertThrows(IllegalArgumentException.class, () -> persisted.shareThreatWithGroup(groupId, second, java.util.Set.of(first, second), false));
    }

    @Test void bothUnhiddenSidesAutomaticallyKnowAndEnemyHeardNotificationTransfersAwareness() {
        UUID group = UUID.randomUUID(), first = UUID.randomUUID(), second = UUID.randomUUID();
        var cycle = ExplorationCycle.start(UUID.randomUUID(), Map.of(group, 10)).finishGroupTurn(group, false);
        var bothUnhidden = new ExplorationCycle.ThreatDiscovery(java.util.Set.of(first), false, false, true);
        var proposal = new ExplorationCycle.EnvironmentProposal("", java.util.List.of())
                .withDiscovery(bothUnhidden, java.util.Set.of(first, second));
        assertEquals(java.util.Set.of(first, second), proposal.awareCharacterSheetIds());
        assertTrue(proposal.enemiesAware());

        var notificationHeard = new ExplorationCycle.ThreatDiscovery(java.util.Set.of(first), false, true, false);
        proposal = new ExplorationCycle.EnvironmentProposal("", java.util.List.of()).withDiscovery(
                notificationHeard, java.util.Set.of(first, second));
        cycle = cycle.withEnvironmentProposal(proposal);
        var shared = cycle.shareThreatWithGroup(group, first, java.util.Set.of(first, second), true);
        assertTrue(shared.pendingEnvironmentProposal().enemiesAware());
        assertEquals(java.util.Set.of(first, second), shared.pendingEnvironmentProposal().awareCharacterSheetIds());
    }
}
