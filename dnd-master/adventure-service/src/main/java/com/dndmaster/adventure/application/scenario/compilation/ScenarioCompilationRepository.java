package com.dndmaster.adventure.application.scenario.compilation;

import com.dndmaster.adventure.domain.scenario.ScenarioCompilation;
import java.util.Optional;
import java.util.UUID;

public interface ScenarioCompilationRepository {
    Optional<ScenarioCompilation> findById(UUID id);
    void save(ScenarioCompilation compilation);
}
