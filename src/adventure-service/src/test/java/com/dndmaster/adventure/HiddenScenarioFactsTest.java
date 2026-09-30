package com.dndmaster.adventure;

import static org.assertj.core.api.Assertions.assertThat;

import com.dndmaster.adventure.application.runtime.HiddenScenarioFacts;
import com.dndmaster.adventure.domain.runtime.story.RevelationStatus;
import com.dndmaster.adventure.domain.runtime.story.StoryRuntimeState;
import com.dndmaster.adventure.domain.scenario.ScenarioModel;
import com.dndmaster.adventure.domain.scenario.ScenarioModelElement;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HiddenScenarioFactsTest {
    @Test
    void returns_individual_unlearned_revelation_values_and_omits_learned_values() {
        var model = new ScenarioModel(1, List.of(), List.of(), List.of(), List.of(
                new ScenarioModelElement("hidden-heir", "revelation", Map.of("value", "The caretaker is the missing heir."), List.of()),
                new ScenarioModelElement("hidden-door", "revelation", Map.of("value", "A door is behind the tapestry."), List.of())),
                List.of(), List.of(), List.of(), "starting situation");
        var state = new StoryRuntimeState(1, UUID.randomUUID(), 1, "stage", 1, Map.of(),
                Map.of("hidden-door", RevelationStatus.LEARNED), Map.of(), null, false, Set.of());

        assertThat(HiddenScenarioFacts.unrevealedRevelationValues(model, state))
                .containsExactly("The caretaker is the missing heir.");
    }
}
