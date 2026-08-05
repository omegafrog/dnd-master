package com.dndmaster.adventure.domain.runtime.plan;

import com.dndmaster.adventure.domain.runtime.fact.CommittedWorldFact;
import com.dndmaster.adventure.domain.runtime.fact.CommittedWorldFactLedger;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class StoryPlanRevisionValidator {
    public AdventureStoryPlanRevision revise(AdventureStoryPlanRevision current, List<String> candidate,
            CommittedWorldFactLedger ledger, List<String> sourceLocks, List<String> rules, UUID causeTurnId) {
        Objects.requireNonNull(current); Objects.requireNonNull(candidate); Objects.requireNonNull(ledger); Objects.requireNonNull(causeTurnId);
        String joined = String.join(" ", candidate).toLowerCase();
        for (CommittedWorldFact fact : ledger.publicFacts()) {
            if (joined.contains(fact.subject().toLowerCase()) && joined.contains(fact.predicate().toLowerCase())
                    && !joined.contains(fact.object().toLowerCase())) throw new IllegalArgumentException("story plan contradicts committed public fact");
        }
        for (String lock : sourceLocks) if (!joined.contains(lock.toLowerCase())) throw new IllegalArgumentException("story plan violates source lock");
        for (String rule : rules) if (rule != null && rule.startsWith("FORBID:") && joined.contains(rule.substring(7).trim().toLowerCase())) throw new IllegalArgumentException("story plan violates rule");
        return new AdventureStoryPlanRevision(UUID.randomUUID(), current.sessionId(), current.version() + 1, current.revisionId(), causeTurnId, candidate);
    }
}
