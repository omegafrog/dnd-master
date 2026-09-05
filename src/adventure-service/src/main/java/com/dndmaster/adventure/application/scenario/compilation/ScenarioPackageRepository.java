package com.dndmaster.adventure.application.scenario.compilation;

import com.dndmaster.adventure.domain.scenario.ScenarioPackage;
import com.dndmaster.adventure.domain.scenario.CharacterCreationBlueprint;
import com.dndmaster.adventure.domain.scenario.ScenarioCompilation;
import com.dndmaster.adventure.domain.scenario.ScenarioCompilationDiagnostic;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

public interface ScenarioPackageRepository {
    Optional<ScenarioPackage> findByInputFingerprint(String inputFingerprint);
    Optional<ScenarioPackage> findById(UUID packageId);
    default List<ScenarioPackage> findByBundleId(UUID bundleId) { return List.of(); }
    void save(ScenarioPackage scenarioPackage);

    /** Persists Package, child ScenarioModel, Job completion, and publication event as one unit when supported. */
    default boolean publishAtomically(ScenarioPackage scenarioPackage, ScenarioCompilation compilation,
            List<ScenarioCompilationDiagnostic> diagnostics) {
        save(scenarioPackage);
        return false;
    }

    default void saveBlueprint(UUID packageId, CharacterCreationBlueprint blueprint) {
        throw new UnsupportedOperationException("blueprint updates are not supported by this repository");
    }
}
