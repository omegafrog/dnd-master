package com.dndmaster.adventure.domain.runtime.narrative;

import java.util.List;
import java.util.Map;
import java.util.Set;

public record NarrativeContext(String actorId, String currentScene, long stateVersion, Set<String> factsKnownBy,
        List<WorldFact> worldFacts, Map<String, CharacterKnowledge> characterKnowledge,
        List<Relationship> relationships, List<ActiveThread> activeThreads, List<RecentEvent> recentEvents) {
    public NarrativeContext {
        factsKnownBy = Set.copyOf(factsKnownBy); worldFacts = List.copyOf(worldFacts);
        characterKnowledge = Map.copyOf(characterKnowledge); relationships = List.copyOf(relationships);
        activeThreads = List.copyOf(activeThreads); recentEvents = List.copyOf(recentEvents);
    }
}
