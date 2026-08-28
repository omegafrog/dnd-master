package com.dndmaster.adventure.application.quality;

import com.dndmaster.adventure.domain.runtime.EffectiveGmProviderSelection;
import com.dndmaster.adventure.domain.runtime.RequestedGmProviderSelection;
import java.util.Objects;

/** Auditable requested, resolved, and invocation-bound provider identity for one turn. */
public record GoldenJourneyProviderAudit(
        RequestedGmProviderSelection requested,
        EffectiveGmProviderSelection effective,
        EffectiveGmProviderSelection actual) {
    public GoldenJourneyProviderAudit {
        requested = Objects.requireNonNull(requested, "requested provider");
        effective = Objects.requireNonNull(effective, "effective provider");
        actual = Objects.requireNonNull(actual, "actual provider");
    }

    public boolean actualMatchesEffective() {
        return actual.equals(effective);
    }

    public boolean requestedMatchesEffective() {
        return requested.endpointId() == null
                ? requested.provider().equalsIgnoreCase(effective.provider())
                : requested.endpointId().equals(effective.endpointId())
                    && requested.provider().equalsIgnoreCase(effective.provider())
                    && requested.model().equals(effective.model())
                    && requested.reasoning().equalsIgnoreCase(effective.reasoning());
    }
}
