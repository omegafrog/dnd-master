package com.dndmaster.adventure.application.scenario.compilation;

import com.dndmaster.adventure.domain.scenario.ResolutionOverride;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleId;
import java.util.List;

public final class NoopResolutionOverrideRepository implements ResolutionOverrideRepository {
    @Override
    public List<ResolutionOverride> findByBundleId(ScenarioBundleId bundleId) {
        return List.of();
    }

    @Override
    public void saveAll(List<ResolutionOverride> overrides) {}
}
