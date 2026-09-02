package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.scenario.ScenarioPackage;
import com.dndmaster.adventure.domain.scenario.ScenarioResolutionDetail;
import com.dndmaster.adventure.domain.scenario.ScenarioResolutionUnit;

public final class DefaultTriggerDetection implements TriggerDetectionPort {
    @Override
    public TriggerDetection detect(TriggerInput input, ScenarioPackage scenarioPackage) {
        if (input == null || scenarioPackage == null) return TriggerDetection.none();
        return scenarioPackage.runtimeCandidates().stream()
                .filter(unit -> unit.trigger() != null)
                .filter(unit -> unit.trigger().type() == (input.worldEvent()
                        ? ScenarioResolutionDetail.TriggerType.WORLD_EVENT
                        : ScenarioResolutionDetail.TriggerType.PLAYER_ACTION))
                .filter(unit -> input.worldEvent() || matches(unit.trigger().condition(), input.action()))
                .findFirst()
                .map(unit -> new TriggerDetection(unit.trigger().type(), unit.trigger().condition(), unit))
                .orElseGet(TriggerDetection::none);
    }

    private static boolean matches(String condition, String action) {
        if (condition == null || condition.isBlank()) return false;
        String normalizedAction = action.toLowerCase(java.util.Locale.ROOT);
        return java.util.Arrays.stream(condition.toLowerCase(java.util.Locale.ROOT).split("\\W+"))
                .filter(token -> token.length() > 2)
                .anyMatch(normalizedAction::contains);
    }
}
