package com.dndmaster.adventure.domain.runtime.fact;

import java.util.List;
import java.util.Objects;

public final class CommittedWorldFactLedger {
    private final List<CommittedWorldFact> facts;
    private CommittedWorldFactLedger(List<CommittedWorldFact> facts) { this.facts = List.copyOf(facts); }
    public static CommittedWorldFactLedger empty() { return new CommittedWorldFactLedger(List.of()); }
    public static CommittedWorldFactLedger of(List<CommittedWorldFact> facts) { return new CommittedWorldFactLedger(Objects.requireNonNull(facts)); }
    public CommittedWorldFactLedger append(CommittedWorldFact fact) {
        Objects.requireNonNull(fact);
        if (facts.stream().anyMatch(existing -> existing.factId().equals(fact.factId())
                || (existing.causeTurnId().equals(fact.causeTurnId()) && existing.sameClaimAs(fact)
                && existing.object().equals(fact.object())))) return this;
        if (facts.stream().anyMatch(existing -> existing.sameClaimAs(fact) && !existing.object().equals(fact.object())))
            throw new IllegalArgumentException("committed fact contradicts existing fact");
        return new CommittedWorldFactLedger(java.util.stream.Stream.concat(facts.stream(), java.util.stream.Stream.of(fact)).toList());
    }
    public List<CommittedWorldFact> facts() { return facts; }
    public List<CommittedWorldFact> publicFacts() { return facts.stream().filter(f -> f.visibility() == FactVisibility.PUBLIC).toList(); }
}
