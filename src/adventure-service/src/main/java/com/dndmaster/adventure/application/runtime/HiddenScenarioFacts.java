package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.runtime.story.RevelationStatus;
import com.dndmaster.adventure.domain.runtime.story.StoryRuntimeState;
import com.dndmaster.adventure.domain.scenario.ScenarioModel;
import com.dndmaster.adventure.domain.scenario.ScenarioModelElement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Selects individual player-hidden values from unrevealed locked scenario revelations. */
public final class HiddenScenarioFacts {
    private HiddenScenarioFacts() {}

    public static List<String> unrevealedRevelationValues(ScenarioModel model, StoryRuntimeState state) {
        if (model == null) return List.of();
        List<String> values = new ArrayList<>();
        for (ScenarioModelElement revelation : model.revelations()) {
            if (state != null && state.revelationStatuses().get(revelation.elementId()) == RevelationStatus.LEARNED) continue;
            collectText(revelation.attributes(), values);
        }
        return values.stream().filter(value -> !value.isBlank()).map(String::trim).distinct().toList();
    }

    private static void collectText(Object value, List<String> output) {
        if (value instanceof String text) {
            if (!text.isBlank()) output.add(text.trim());
        } else if (value instanceof Map<?, ?> map) {
            map.values().forEach(item -> collectText(item, output));
        } else if (value instanceof Iterable<?> items) {
            items.forEach(item -> collectText(item, output));
        }
    }
}
