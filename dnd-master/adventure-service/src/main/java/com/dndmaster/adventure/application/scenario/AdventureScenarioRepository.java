package com.dndmaster.adventure.application.scenario;

import com.dndmaster.adventure.domain.scenario.AdventureScenario;
import com.dndmaster.adventure.domain.scenario.ScenarioId;
import java.util.Optional;

public interface AdventureScenarioRepository {
    Optional<AdventureScenario> findById(ScenarioId scenarioId);

    void save(AdventureScenario scenario);
}
