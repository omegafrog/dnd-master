package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.adventure.application.runtime.CompletionProposal;
import com.dndmaster.adventure.application.runtime.PendingRuntimeState;
import com.dndmaster.adventure.application.runtime.RuntimeAddedFactCandidate;
import com.dndmaster.adventure.application.runtime.RuntimeFallbackFactPolicy;
import com.dndmaster.adventure.application.runtime.RuntimeFactLookupResult;
import com.dndmaster.adventure.application.runtime.RuntimeTurnLifecycle;
import com.dndmaster.adventure.application.runtime.RuntimeTurn;
import com.dndmaster.adventure.application.runtime.RuntimeTurnResolution;
import com.dndmaster.adventure.application.runtime.RuntimeTurnSafetyOrchestrator;
import com.dndmaster.adventure.application.runtime.EvidencePack;
import com.dndmaster.adventure.application.runtime.RuntimePlan;
import com.dndmaster.adventure.application.runtime.NarrationSafetyAssessment;
import com.dndmaster.adventure.application.runtime.SituationUpdatePolicy;
import com.dndmaster.adventure.application.runtime.SituationUpdateProposal;
import com.dndmaster.adventure.domain.adventure.Adventure;
import com.dndmaster.adventure.domain.adventure.AdventureContext;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.CharacterSheetId;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import com.dndmaster.adventure.domain.adventure.RuleSetId;
import com.dndmaster.adventure.domain.adventure.ScenarioId;
import com.dndmaster.adventure.domain.adventure.SessionId;
import com.dndmaster.adventure.domain.runtime.CurrentSituation;
import com.dndmaster.adventure.domain.runtime.DisclosureState;
import com.dndmaster.adventure.domain.runtime.GameState;
import com.dndmaster.adventure.domain.runtime.GameStateDelta;
import com.dndmaster.adventure.domain.runtime.RuntimeAddedFact;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ScenarioRuntimeTurnSafetyTest {
    @Test
    void target_runtime_turn_lifecycle_requires_resolution_before_narration_and_commit() {
        assertTrue(RuntimeTurnLifecycle.REQUESTED.canTransitionTo(RuntimeTurnLifecycle.RESOLVING));
        assertTrue(RuntimeTurnLifecycle.RESOLVING.canTransitionTo(RuntimeTurnLifecycle.RESOLUTION_FIXED));
        assertTrue(RuntimeTurnLifecycle.RESOLUTION_FIXED.canTransitionTo(RuntimeTurnLifecycle.NARRATING));
        assertTrue(RuntimeTurnLifecycle.NARRATING.canTransitionTo(RuntimeTurnLifecycle.SAFETY_CHECKING));
        assertTrue(RuntimeTurnLifecycle.SAFETY_CHECKING.canTransitionTo(RuntimeTurnLifecycle.NARRATING));
        assertTrue(RuntimeTurnLifecycle.SAFETY_CHECKING.canTransitionTo(RuntimeTurnLifecycle.READY_TO_COMMIT));
        assertTrue(RuntimeTurnLifecycle.READY_TO_COMMIT.canTransitionTo(RuntimeTurnLifecycle.COMMITTING));
        assertTrue(RuntimeTurnLifecycle.COMMITTING.canTransitionTo(RuntimeTurnLifecycle.COMMITTED));
        assertFalse(RuntimeTurnLifecycle.RESOLUTION_FIXED.canTransitionTo(RuntimeTurnLifecycle.COMMITTED));
        assertFalse(RuntimeTurnLifecycle.NARRATING.canTransitionTo(RuntimeTurnLifecycle.COMMITTING));
        assertThrows(IllegalStateException.class,
                () -> RuntimeTurnLifecycle.RESOLUTION_FIXED.transitionTo(RuntimeTurnLifecycle.COMMITTED));
    }

    @Test
    void situation_continues_with_same_id_and_transitions_only_for_material_change() {
        CurrentSituation current = new CurrentSituation(UUID.randomUUID(), 2, "harbor", "sealed gate", "guard", "enter");

        CurrentSituation continued = SituationUpdatePolicy.apply(current,
                SituationUpdateProposal.continueSituation("sealed gate", "guard", "enter"));
        assertEquals(current.situationId(), continued.situationId());
        assertEquals(3, continued.revision());

        CurrentSituation unchangedTransition = SituationUpdatePolicy.apply(current,
                SituationUpdateProposal.transition("harbor", "sealed gate", "guard", "enter"));
        assertEquals(current.situationId(), unchangedTransition.situationId());

        CurrentSituation transitioned = SituationUpdatePolicy.apply(current,
                SituationUpdateProposal.transition("undercroft", "flooded tunnel", "rising water", "escape"));
        assertTrue(!current.situationId().equals(transitioned.situationId()));
        assertEquals(1, transitioned.revision());
    }

    @Test
    void fallback_is_created_only_after_not_found_and_never_retcons_existing_fact() {
        UUID turnId = UUID.randomUUID();
        RuntimeAddedFact existing = new RuntimeAddedFact(UUID.randomUUID(), "The north door is locked.", UUID.randomUUID());
        RuntimeAddedFactCandidate candidate = new RuntimeAddedFactCandidate("north door", "The north door is open.");

        assertTrue(RuntimeFallbackFactPolicy.create(RuntimeFactLookupResult.found(
                RuntimeFactLookupResult.Source.STORYBOOK_RAG, "The north door is locked."), candidate, turnId,
                GameState.empty(), List.of(existing)).isEmpty());
        assertTrue(RuntimeFallbackFactPolicy.create(RuntimeFactLookupResult.notFound(), candidate, turnId,
                GameState.empty(), List.of(existing)).isEmpty());

        RuntimeAddedFact created = RuntimeFallbackFactPolicy.create(RuntimeFactLookupResult.notFound(),
                new RuntimeAddedFactCandidate("missing sibling", "Harl has a sister named Mara."), turnId,
                GameState.empty(), List.of(existing)).orElseThrow();
        assertEquals(turnId, created.establishedTurnId());
    }

    @Test
    void canonical_runtime_commit_is_atomic_and_completion_is_applied_with_the_same_state_change() {
        OwnerPlayerId owner = new OwnerPlayerId(UUID.randomUUID());
        Adventure adventure = Adventure.rehydrateWithRuntimeState(
                AdventureId.generate(), SessionId.generate(), owner, new ScenarioId(UUID.randomUUID()),
                new RuleSetId(UUID.randomUUID()), List.of(new com.dndmaster.adventure.domain.adventure.AdventurePartyMember(
                        new CharacterSheetId(UUID.randomUUID()), com.dndmaster.adventure.domain.adventure.ControlMode.DIRECT,
                        true, true, true, true, true, true)), List.of(),
                new AdventureContext("gate", "guard", "", "waiting"),
                com.dndmaster.adventure.domain.adventure.AdventureStatus.ACTIVE, 4, 0, null,
                UUID.randomUUID(), 1, new GameState(Map.of("door", "closed"), 2), DisclosureState.empty(),
                new CurrentSituation(UUID.randomUUID(), 1, "harbor", "gate", "guard", "enter"), List.of());
        UUID turnId = UUID.randomUUID();
        RuntimeAddedFact fact = new RuntimeAddedFact(UUID.randomUUID(), "Mara is in the undercroft.", turnId);
        CurrentSituation next = new CurrentSituation(UUID.randomUUID(), 1, "undercroft", "flooded tunnel", "water", "escape");
        PendingRuntimeState pending = new PendingRuntimeState(
                new GameStateDelta(Map.of("door", "open")), DisclosureState.empty(), next, List.of(fact));

        adventure.commitRuntimeTurn(owner, 4, pending,
                new AdventureContext("undercroft", "water", "Open the door", "The door opens."),
                List.of(), new CompletionProposal(true, "The adventure concludes at the undercroft."));

        assertEquals("open", adventure.gameState().values().get("door"));
        assertEquals(List.of(fact), adventure.runtimeAddedFacts());
        assertEquals(next, adventure.currentSituation());
        assertEquals(com.dndmaster.adventure.domain.adventure.AdventureStatus.COMPLETED, adventure.status());
        assertEquals(5, adventure.version());
    }

    @Test
    void unsafe_narration_retry_keeps_fixed_resolution_and_pending_state_stable() {
        AdventureContext context = new AdventureContext("gate", "guard", "", "waiting");
        RuntimeTurn requested = new RuntimeTurn(UUID.randomUUID(), UUID.randomUUID(), AdventureId.generate(), UUID.randomUUID(),
                UUID.randomUUID(), 1, "Open the door", new EvidencePack(List.of(), List.of(), List.of()),
                new RuntimePlan("gate", "guard", "rolled 17", "draft", null, List.of(), List.of()), null,
                context, List.of(), 0, List.of(), List.of(), false, true,
                com.dndmaster.adventure.application.runtime.RuntimeTurnOrigin.PLAYER, true);
        PendingRuntimeState pending = new PendingRuntimeState(new GameStateDelta(Map.of("door", "open")),
                DisclosureState.empty(), CurrentSituation.initial("gate"), List.of());
        RuntimeTurnResolution fixed = new RuntimeTurnResolution("success", 17, List.of("success"));
        java.util.concurrent.atomic.AtomicInteger writes = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.atomic.AtomicInteger checks = new java.util.concurrent.atomic.AtomicInteger();
        RuntimeTurnSafetyOrchestrator orchestrator = new RuntimeTurnSafetyOrchestrator(
                request -> checks.getAndIncrement() == 0
                        ? new NarrationSafetyAssessment(false, "hidden fact")
                        : new NarrationSafetyAssessment(true, "safe"));

        RuntimeTurn ready = orchestrator.resolveAndNarrate(requested, fixed, pending,
                CompletionProposal.continueAdventure(), () -> writes.incrementAndGet() == 1 ? "unsafe" : "safe narration");

        assertEquals(RuntimeTurnLifecycle.READY_TO_COMMIT, ready.lifecycle());
        assertEquals(fixed, ready.fixedResolution());
        assertEquals(pending, ready.pendingState());
        assertEquals("safe narration", ready.narration());
        assertEquals(2, writes.get());
        assertNotNull(ready.completionProposal());
    }
}
