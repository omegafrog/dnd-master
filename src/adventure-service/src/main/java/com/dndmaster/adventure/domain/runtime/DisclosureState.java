package com.dndmaster.adventure.domain.runtime;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import com.fasterxml.jackson.annotation.JsonProperty;

/** The monotonic set of scenario facts the player may know. */
public final class DisclosureState {
    private final Set<String> disclosedFactIds;

    public DisclosureState(Collection<String> disclosedFactIds) {
        Objects.requireNonNull(disclosedFactIds, "disclosed facts must not be null");
        Set<String> copy = new LinkedHashSet<>();
        for (String id : disclosedFactIds) {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("disclosed fact ids must not be blank");
            copy.add(id.trim());
        }
        this.disclosedFactIds = Set.copyOf(copy);
    }

    public static DisclosureState empty() { return new DisclosureState(Set.of()); }
    @JsonProperty("disclosedFactIds") public Set<String> disclosedFactIds() { return disclosedFactIds; }
    public boolean contains(String factId) { return disclosedFactIds.contains(factId); }
}
