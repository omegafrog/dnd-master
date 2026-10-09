package com.dndmaster.adventure.application.scenario.preparation;

import com.dndmaster.adventure.domain.scenario.ScenarioSourceReference;
import java.util.List;
import java.util.UUID;
import java.util.Set;

@FunctionalInterface
public interface StorybookEvidenceLookupPort {
    List<ScenarioSourceReference> lookup(UUID scenarioPackageId);
    default List<ScenarioSourceReference> lookup(UUID scenarioPackageId,
            com.dndmaster.adventure.domain.adventure.SessionId sessionId) { return lookup(scenarioPackageId); }

    /** True when the package has scenario-document claims that require provenance. */
    default boolean isScenarioBacked(UUID scenarioPackageId) { return true; }
    default Set<UUID> providedMapIds(UUID scenarioPackageId) { return Set.of(); }
}
