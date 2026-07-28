package com.dndmaster.adventure.application.scenario.compilation;

import com.dndmaster.adventure.domain.scenario.ScenarioPackage;
import com.dndmaster.adventure.domain.scenario.CharacterCreationBlueprint;
import java.util.Optional;
import java.util.UUID;

public interface ScenarioPackageRepository {
    Optional<ScenarioPackage> findByInputFingerprint(String inputFingerprint);
    Optional<ScenarioPackage> findById(UUID packageId);
    void save(ScenarioPackage scenarioPackage);

    default void saveBlueprint(UUID packageId, CharacterCreationBlueprint blueprint) {
        throw new UnsupportedOperationException("blueprint updates are not supported by this repository");
    }
}
