package com.dndmaster.adventure.application.scenario.compilation;

import com.dndmaster.adventure.domain.scenario.ScenarioPackage;
import java.util.Optional;
import java.util.UUID;

public interface ScenarioPackageRepository {
    Optional<ScenarioPackage> findByInputFingerprint(String inputFingerprint);
    Optional<ScenarioPackage> findById(UUID packageId);
    void save(ScenarioPackage scenarioPackage);
}
