package com.dndmaster.adventure.application.scenario.preparation;

import com.dndmaster.adventure.domain.scenario.ScenarioSourceReference;
import java.util.List;
import java.util.UUID;

@FunctionalInterface
public interface StorybookEvidenceLookupPort {
    List<ScenarioSourceReference> lookup(UUID scenarioPackageId);

    /** True when the package has scenario-document claims that require provenance. */
    default boolean isScenarioBacked(UUID scenarioPackageId) { return true; }
}
