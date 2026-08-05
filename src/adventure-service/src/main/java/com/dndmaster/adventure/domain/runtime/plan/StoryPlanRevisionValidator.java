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
        for (CommittedWorldFact fact : ledger.facts()) {
            if (joined.contains(fact.subject().toLowerCase()) && joined.contains(fact.predicate().toLowerCase())
                    && !joined.contains(fact.object().toLowerCase())) throw new IllegalArgumentException("story plan contradicts committed public fact");
        }
        for (String lock : sourceLocks) {
            if (lock == null || lock.isBlank()) continue;
            if (lock.startsWith("FORBID:")) {
                if (joined.contains(lock.substring(7).trim().toLowerCase())) throw new IllegalArgumentException("story plan violates source lock");
            } else if (lock.contains("|")) {
                String[] parts = lock.toLowerCase().split("\\|", -1);
                if (parts.length != 3) throw new IllegalArgumentException("invalid source lock");
                if (joined.contains(parts[0].trim()) && joined.contains(parts[1].trim()) && !joined.contains(parts[2].trim()))
                    throw new IllegalArgumentException("story plan contradicts source lock");
            }
        }
        for (String rule : rules) if (rule != null && rule.toUpperCase().startsWith("FORBID:")
                && joined.contains(rule.substring(7).trim().toLowerCase())) throw new IllegalArgumentException("story plan violates rule");
        return new AdventureStoryPlanRevision(UUID.randomUUID(), current.sessionId(), current.version() + 1, current.revisionId(), causeTurnId, candidate);
    }
}
