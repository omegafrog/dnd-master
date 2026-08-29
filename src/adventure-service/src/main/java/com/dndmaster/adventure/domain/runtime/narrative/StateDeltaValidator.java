package com.dndmaster.adventure.domain.runtime.narrative;

import java.util.LinkedHashMap;
import java.util.Map;

public final class StateDeltaValidator {
    public NarrativeState validateAndCommit(NarrativeState state, StateDelta delta) {
        if (state.version() != delta.expectedVersion()) throw new IllegalStateException("state version conflict: expected "
                + delta.expectedVersion() + " but was " + state.version());
        for (String id : delta.changedFactIds()) if (!state.worldFacts().containsKey(id)) throw new IllegalArgumentException("unknown fact: " + id);
        for (String id : delta.revealedFactIds()) if (!state.worldFacts().containsKey(id)) throw new IllegalArgumentException("unknown fact: " + id);
        Map<String, RevealedFact> reveals = new LinkedHashMap<>();
        for (String id : delta.revealedFactIds()) {
            if (!state.revealedFacts().containsKey(id)) reveals.put(id, new RevealedFact(id, delta.expectedVersion(), "validated state delta"));
        }
        for (CharacterKnowledge change : delta.knowledgeChanges())
            for (String id : change.factIds()) if (!state.worldFacts().containsKey(id)) throw new IllegalArgumentException("unknown fact: " + id);
        for (Belief belief : delta.beliefChanges()) if (!state.worldFacts().containsKey(belief.subjectId()))
            throw new IllegalArgumentException("unknown belief subject: " + belief.subjectId());
        return state.committed(delta, reveals);
    }
}
