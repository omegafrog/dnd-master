package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.runtime.clock.AdventureClock;
import com.dndmaster.adventure.domain.runtime.clock.GameTimePolicy;
import com.dndmaster.adventure.domain.runtime.fact.CommittedWorldFactLedger;
import com.dndmaster.adventure.domain.runtime.plan.AdventureStoryPlanRevision;
import com.dndmaster.adventure.domain.runtime.plan.StoryPlanRevisionValidator;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.UUID;
import org.springframework.transaction.support.TransactionTemplate;

/** Owns continuity mutations. Each command is one local transaction and one cause turn. */
public final class StoryContinuityCommandService {
    private final StoryPlanRevisionRepository plans;
    private final AdventureClockRepository clocks;
    private final CommittedWorldFactRepository facts;
    private final StoryPlanRevisionValidator validator;
    private final TransactionTemplate transaction;

    public StoryContinuityCommandService(StoryPlanRevisionRepository plans, AdventureClockRepository clocks,
            CommittedWorldFactRepository facts, StoryPlanRevisionValidator validator, TransactionTemplate transaction) {
        this.plans = Objects.requireNonNull(plans); this.clocks = Objects.requireNonNull(clocks); this.facts = Objects.requireNonNull(facts);
        this.validator = Objects.requireNonNull(validator); this.transaction = Objects.requireNonNull(transaction);
    }

    public ContinuityCommandResult revise(UUID sessionId, UUID commandId, UUID turnId, List<String> stages, long expectedPlanVersion) {
        return transaction.execute(status -> {
            AdventureStoryPlanRevision current = plans.current(sessionId).orElseThrow(() -> new IllegalStateException("story plan revision not found"));
            if (current.version() != expectedPlanVersion) throw new IllegalStateException("story plan version conflict");
            AdventureStoryPlanRevision next = validator.revise(current, stages, facts.findBySessionId(sessionId), List.of(), List.of(), turnId);
            plans.append(next);
            return new ContinuityCommandResult("story plan revised", next.version(), next.revisionId().toString(), clocks.findBySessionId(sessionId).map(AdventureClock::version).orElse(0L));
        });
    }

    public ContinuityCommandResult advance(UUID sessionId, UUID commandId, UUID turnId, long turns,
            long expectedClockVersion, OptionalInt ruleSecondsPerTurn) {
        return transaction.execute(status -> {
            AdventureClock current = clocks.findBySessionId(sessionId).orElseGet(() -> AdventureClock.initial(sessionId));
            if (current.version() != expectedClockVersion) throw new IllegalStateException("adventure clock version conflict");
            AdventureClock next = current.advance(GameTimePolicy.durationForTurns(turns, ruleSecondsPerTurn), turnId);
            clocks.save(next, expectedClockVersion);
            long planVersion = plans.current(sessionId).map(AdventureStoryPlanRevision::version).orElse(0L);
            String planId = plans.current(sessionId).map(p -> p.revisionId().toString()).orElse("");
            return new ContinuityCommandResult("game time advanced", next.version(), planId, next.version());
        });
    }
}
