package com.dndmaster.adventure.domain.runtime.narrative;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Canonical, session-scoped narrative reality. Evidence and projections never mutate it. */
public final class NarrativeState {
    @JsonProperty("version") private final long version;
    @JsonProperty("worldFacts") private final Map<String, WorldFact> worldFacts;
    @JsonProperty("revealedFacts") private final Map<String, RevealedFact> revealedFacts;
    @JsonProperty("characterKnowledge") private final Map<String, CharacterKnowledge> characterKnowledge;
    @JsonProperty("relationships") private final List<Relationship> relationships;
    @JsonProperty("activeThreads") private final List<ActiveThread> activeThreads;
    @JsonProperty("recentEvents") private final List<RecentEvent> recentEvents;

    @JsonCreator
    public NarrativeState(@JsonProperty("version") long version,
            @JsonProperty("worldFacts") Map<String, WorldFact> worldFacts,
            @JsonProperty("revealedFacts") Map<String, RevealedFact> revealedFacts,
            @JsonProperty("characterKnowledge") Map<String, CharacterKnowledge> characterKnowledge,
            @JsonProperty("relationships") List<Relationship> relationships,
            @JsonProperty("activeThreads") List<ActiveThread> activeThreads,
            @JsonProperty("recentEvents") List<RecentEvent> recentEvents) {
        this.version = version; this.worldFacts = Map.copyOf(worldFacts == null ? Map.of() : worldFacts); this.revealedFacts = Map.copyOf(revealedFacts == null ? Map.of() : revealedFacts);
        this.characterKnowledge = Map.copyOf(characterKnowledge == null ? Map.of() : characterKnowledge); this.relationships = List.copyOf(relationships == null ? List.of() : relationships);
        this.activeThreads = List.copyOf(activeThreads == null ? List.of() : activeThreads); this.recentEvents = List.copyOf(recentEvents == null ? List.of() : recentEvents);
    }
    public static NarrativeState empty() { return new NarrativeState(0, Map.of(), Map.of(), Map.of(), List.of(), List.of(), List.of()); }
    public long version() { return version; }
    public Map<String, WorldFact> worldFacts() { return worldFacts; }
    public Map<String, RevealedFact> revealedFacts() { return revealedFacts; }
    public Map<String, CharacterKnowledge> characterKnowledge() { return characterKnowledge; }
    public List<Relationship> relationships() { return relationships; }
    public List<ActiveThread> activeThreads() { return activeThreads; }
    public List<RecentEvent> recentEvents() { return recentEvents; }
    public Set<String> factsKnownBy(String actorId) {
        if (actorId == null || actorId.isBlank()) throw new IllegalArgumentException("actor id must not be blank");
        Set<String> result = new LinkedHashSet<>(revealedFacts.keySet());
        CharacterKnowledge knowledge = characterKnowledge.get(actorId.trim());
        if (knowledge != null) result.addAll(knowledge.factIds());
        return Set.copyOf(result);
    }
    public boolean characterKnows(String actorId, String factId) { return factsKnownBy(actorId).contains(factId); }
    public boolean canReveal(String factId) { return worldFacts.containsKey(factId) && !revealedFacts.containsKey(factId); }
    public NarrativeContext project(String actorId, String currentScene) {
        Set<String> known = factsKnownBy(actorId);
        List<WorldFact> visible = known.stream().map(worldFacts::get).filter(java.util.Objects::nonNull).toList();
        CharacterKnowledge own = characterKnowledge.get(actorId);
        Map<String, CharacterKnowledge> scopedKnowledge = own == null ? Map.of() : Map.of(actorId, own);
        return new NarrativeContext(actorId, currentScene, version, known, visible, scopedKnowledge,
                relationships, activeThreads, recentEvents);
    }
    public NarrativeState addWorldFact(WorldFact fact) {
        Map<String, WorldFact> facts = new LinkedHashMap<>(worldFacts); facts.put(fact.id(), fact);
        return copy(version, facts, revealedFacts, characterKnowledge, relationships, activeThreads, recentEvents);
    }
    public NarrativeState recordKnowledge(String actorId, String factId) {
        CharacterKnowledge prior = characterKnowledge.getOrDefault(actorId, new CharacterKnowledge(actorId, Set.of(), Set.of()));
        Set<String> facts = new LinkedHashSet<>(prior.factIds()); facts.add(factId);
        Map<String, CharacterKnowledge> next = new LinkedHashMap<>(characterKnowledge);
        next.put(actorId, new CharacterKnowledge(actorId, facts, prior.beliefs()));
        return copy(version, worldFacts, revealedFacts, next, relationships, activeThreads, recentEvents);
    }
    public NarrativeState recordBelief(Belief belief) {
        CharacterKnowledge prior = characterKnowledge.getOrDefault(belief.actorId(), new CharacterKnowledge(belief.actorId(), Set.of(), Set.of()));
        Set<Belief> beliefs = new LinkedHashSet<>(prior.beliefs()); beliefs.removeIf(existing -> existing.subjectId().equals(belief.subjectId())); beliefs.add(belief);
        Map<String, CharacterKnowledge> next = new LinkedHashMap<>(characterKnowledge);
        next.put(belief.actorId(), new CharacterKnowledge(belief.actorId(), prior.factIds(), beliefs));
        return copy(version, worldFacts, revealedFacts, next, relationships, activeThreads, recentEvents);
    }
    public NarrativeState revealFact(String factId, long turn, String source) {
        if (!canReveal(factId)) { if (revealedFacts.containsKey(factId)) return this; throw new IllegalArgumentException("unknown fact: " + factId); }
        Map<String, RevealedFact> next = new LinkedHashMap<>(revealedFacts); next.put(factId, new RevealedFact(factId, turn, source));
        return copy(version, worldFacts, next, characterKnowledge, relationships, activeThreads, recentEvents);
    }
    public NarrativeState hideFact(String factId) { if (revealedFacts.containsKey(factId)) throw new IllegalStateException("revealed facts are monotonic"); return this; }
    NarrativeState committed(StateDelta delta, Map<String, RevealedFact> reveals) {
        Map<String, RevealedFact> nextReveals = new LinkedHashMap<>(revealedFacts); nextReveals.putAll(reveals);
        Map<String, CharacterKnowledge> knowledge = new LinkedHashMap<>(characterKnowledge);
        for (CharacterKnowledge change : delta.knowledgeChanges()) knowledge.put(change.actorId(), change);
        for (Belief belief : delta.beliefChanges()) {
            CharacterKnowledge prior = knowledge.getOrDefault(belief.actorId(), new CharacterKnowledge(belief.actorId(), Set.of(), Set.of()));
            Set<Belief> beliefs = new LinkedHashSet<>(prior.beliefs()); beliefs.removeIf(b -> b.subjectId().equals(belief.subjectId())); beliefs.add(belief);
            knowledge.put(belief.actorId(), new CharacterKnowledge(belief.actorId(), prior.factIds(), beliefs));
        }
        return copy(version + 1, worldFacts, nextReveals, knowledge, delta.relationshipChanges(), delta.threadChanges(), delta.events());
    }
    private NarrativeState copy(long v, Map<String, WorldFact> f, Map<String, RevealedFact> r, Map<String, CharacterKnowledge> k,
            List<Relationship> rel, List<ActiveThread> threads, List<RecentEvent> events) { return new NarrativeState(v, f, r, k, rel, threads, events); }
}
