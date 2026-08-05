package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.runtime.clock.AdventureClock;
import com.dndmaster.adventure.domain.runtime.fact.CommittedWorldFact;
import com.dndmaster.adventure.domain.runtime.plan.AdventureStoryPlanRevision;
import java.util.List;
import java.util.Objects;

/** Provider input for continuity state. GM-only facts stay out of player projections, not out of this context. */
public record StoryContinuityContext(AdventureStoryPlanRevision plan, List<CommittedWorldFact> facts, AdventureClock clock) {
    public StoryContinuityContext {
        plan = Objects.requireNonNull(plan); facts = List.copyOf(Objects.requireNonNull(facts)); clock = Objects.requireNonNull(clock);
    }
    public String promptText() {
        String factText = facts.stream().map(f -> f.visibility() + ":" + f.subject() + " " + f.predicate() + " " + f.object()).reduce((a, b) -> a + " | " + b).orElse("none");
        return "planRevision=" + plan.revisionId() + "; planVersion=" + plan.version() + "; stages=" + String.join(" -> ", plan.stages())
                + "; facts=" + factText + "; clockVersion=" + clock.version() + "; elapsedTurns=" + clock.turnsElapsed()
                + "; elapsedSeconds=" + clock.secondsElapsed();
    }
}
