package com.dndmaster.adventure.application.scenario.compilation;

import com.dndmaster.adventure.domain.scenario.ScenarioCompilation;
import java.util.Optional;
import java.util.UUID;

public interface ScenarioCompilationRepository {
    Optional<ScenarioCompilation> findById(UUID id);
    default Optional<ScenarioCompilation> findByInputFingerprint(String fingerprint) { return Optional.empty(); }
    void save(ScenarioCompilation compilation);

    default boolean saveIfLeaseMatches(ScenarioCompilation compilation, UUID expectedLeaseToken) {
        Optional<ScenarioCompilation> current = findById(compilation.id());
        if (current.isEmpty() || !java.util.Objects.equals(current.get().leaseToken(), expectedLeaseToken)) {
            return false;
        }
        save(compilation);
        return true;
    }
}
