package com.dndmaster.adventure.domain.runtime.narrative;

import java.util.List;
import java.util.Set;

public record StateDelta(long expectedVersion, Set<String> changedFactIds, Set<String> revealedFactIds,
        List<CharacterKnowledge> knowledgeChanges, List<Belief> beliefChanges, List<Relationship> relationshipChanges,
        List<ActiveThread> threadChanges, List<RecentEvent> events, List<WorldFact> proposedFacts) {
    public StateDelta(long expectedVersion, Set<String> changedFactIds, Set<String> revealedFactIds,
            List<CharacterKnowledge> knowledgeChanges, List<Belief> beliefChanges, List<Relationship> relationshipChanges,
            List<ActiveThread> threadChanges, List<RecentEvent> events) {
        this(expectedVersion, changedFactIds, revealedFactIds, knowledgeChanges, beliefChanges, relationshipChanges,
                threadChanges, events, List.of());
    }

    public static StateDelta proposing(long expectedVersion, List<WorldFact> proposedFacts) {
        return new StateDelta(expectedVersion, Set.of(), Set.of(), List.of(), List.of(), List.of(), List.of(), List.of(), proposedFacts);
    }

    public StateDelta {
        if (expectedVersion < 0) throw new IllegalArgumentException("expected version must not be negative");
        changedFactIds = Set.copyOf(changedFactIds == null ? Set.of() : changedFactIds);
        revealedFactIds = Set.copyOf(revealedFactIds == null ? Set.of() : revealedFactIds);
        knowledgeChanges = List.copyOf(knowledgeChanges == null ? List.of() : knowledgeChanges);
        beliefChanges = List.copyOf(beliefChanges == null ? List.of() : beliefChanges);
        relationshipChanges = List.copyOf(relationshipChanges == null ? List.of() : relationshipChanges);
        threadChanges = List.copyOf(threadChanges == null ? List.of() : threadChanges);
        events = List.copyOf(events == null ? List.of() : events);
        proposedFacts = List.copyOf(proposedFacts == null ? List.of() : proposedFacts);
    }
}
