package com.dndmaster.adventure.application.scenario;

import com.dndmaster.adventure.domain.scenario.ScenarioBundleId;
import com.dndmaster.adventure.domain.scenario.ScenarioSourceBundle;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

public interface ScenarioBundleRepository {
    Optional<ScenarioSourceBundle> findById(ScenarioBundleId bundleId);

    default void deleteById(ScenarioBundleId bundleId) {
        throw new UnsupportedOperationException("scenario bundle deletion is not supported by this repository");
    }

    default boolean hasActiveAdventureReferences(ScenarioBundleId bundleId) { return false; }

    default List<ScenarioSourceBundle> findByOwnerId(UUID ownerPlayerId) {
        return List.of();
    }

    void save(ScenarioSourceBundle bundle);
}
