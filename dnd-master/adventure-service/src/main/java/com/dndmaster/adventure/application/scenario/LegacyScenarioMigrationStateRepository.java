package com.dndmaster.adventure.application.scenario;

import com.dndmaster.adventure.domain.scenario.ScenarioId;
import java.util.Optional;

public interface LegacyScenarioMigrationStateRepository {
    Optional<LegacyScenarioMigrationApplicationService.LegacyScenarioMigrationResult> findByScenarioIdAndSourceHash(
            ScenarioId scenarioId, String sourceHash);
    void save(
            ScenarioId scenarioId,
            String sourceHash,
            LegacyScenarioMigrationApplicationService.LegacyScenarioMigrationResult result);
}
