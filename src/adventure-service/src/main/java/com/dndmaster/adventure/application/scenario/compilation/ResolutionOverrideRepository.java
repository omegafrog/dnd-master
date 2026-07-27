package com.dndmaster.adventure.application.scenario.compilation;

import com.dndmaster.adventure.domain.scenario.ResolutionOverride;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleId;
import java.util.List;

public interface ResolutionOverrideRepository {
    List<ResolutionOverride> findByBundleId(ScenarioBundleId bundleId);
    void saveAll(List<ResolutionOverride> overrides);
}
