package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.runtime.story.RevelationStatus;
import com.dndmaster.adventure.domain.runtime.story.StoryRuntimeState;
import com.dndmaster.adventure.domain.scenario.ScenarioModel;
import com.dndmaster.adventure.domain.scenario.ScenarioModelElement;
import com.dndmaster.adventure.domain.scenario.ScenarioSourceReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Selects individual player-hidden values from unrevealed locked scenario revelations. */
public final class HiddenScenarioFacts {
    private HiddenScenarioFacts() {}

    public static List<String> unrevealedRevelationValues(ScenarioModel model, StoryRuntimeState state) {
        if (model == null) return List.of();
        List<String> values = new ArrayList<>();
        for (ScenarioModelElement revelation : unrevealedRevelations(model, state)) {
            Object value = revelation.attributes().get("value");
            if (value instanceof String text && !text.isBlank()) values.add(text.trim());
        }
        return values.stream().distinct().toList();
    }

    public static ScenarioModel withoutUnrevealedRevelations(ScenarioModel model, StoryRuntimeState state) {
        if (model == null) return null;
        Set<String> hiddenIds = unrevealedRevelations(model, state).stream()
                .map(ScenarioModelElement::elementId).collect(Collectors.toSet());
        return new ScenarioModel(model.schemaVersion(), model.actors(), model.locations(), model.objectives(),
                model.revelations().stream().filter(element -> !hiddenIds.contains(element.elementId())).toList(),
                model.encounters(), model.relationships(), model.resolutionCriteria(), model.startingSituation());
    }

    public static Set<ScenarioSourceReference> unrevealedRevelationSourceRefs(ScenarioModel model, StoryRuntimeState state) {
        return unrevealedRevelations(model, state).stream()
                .flatMap(revelation -> revelation.sourceRefs().stream()).collect(Collectors.toUnmodifiableSet());
    }

    public static Set<String> unrevealedRevelationIds(ScenarioModel model, StoryRuntimeState state) {
        return unrevealedRevelations(model, state).stream()
                .map(ScenarioModelElement::elementId).collect(Collectors.toUnmodifiableSet());
    }

    private static List<ScenarioModelElement> unrevealedRevelations(ScenarioModel model, StoryRuntimeState state) {
        if (model == null) return List.of();
        return model.revelations().stream()
                .filter(revelation -> state == null
                        || state.revelationStatuses().get(revelation.elementId()) != RevelationStatus.LEARNED)
                .toList();
    }
}
