package com.dndmaster.adventure.application.scenario;

import com.dndmaster.adventure.domain.scenario.ScenarioBundleId;
import com.dndmaster.adventure.domain.scenario.ScenarioSourceBundle;
import java.util.Optional;

public interface ScenarioBundleRepository {
    Optional<ScenarioSourceBundle> findById(ScenarioBundleId bundleId);

    void save(ScenarioSourceBundle bundle);
}
