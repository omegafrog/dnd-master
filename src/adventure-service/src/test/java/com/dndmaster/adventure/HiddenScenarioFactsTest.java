package com.dndmaster.adventure;

import static org.assertj.core.api.Assertions.assertThat;

import com.dndmaster.adventure.application.runtime.HiddenScenarioFacts;
import com.dndmaster.adventure.domain.runtime.story.RevelationStatus;
import com.dndmaster.adventure.domain.runtime.story.StoryRuntimeState;
import com.dndmaster.adventure.domain.scenario.ScenarioModel;
import com.dndmaster.adventure.domain.scenario.ScenarioModelElement;
import com.dndmaster.adventure.domain.scenario.ScenarioSourceReference;
import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
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

    @Test
    void combat_projection_removes_unlearned_revelations_but_keeps_learned_ones_and_nonsecret_context() {
        var secretSource = new ScenarioSourceReference(new KnowledgeDocumentId(UUID.randomUUID()), 2, "page:secret");
        var knownSource = new ScenarioSourceReference(new KnowledgeDocumentId(UUID.randomUUID()), 3, "page:known");
        var model = new ScenarioModel(1,
                List.of(new ScenarioModelElement("gatekeeper", "actor", Map.of("name", "Guard"), List.of())),
                List.of(), List.of(), List.of(
                        new ScenarioModelElement("hidden-heir", "revelation", Map.of("value", "The caretaker is the missing heir.",
                                "status", "UNKNOWN", "shortCode", "R-17"), List.of(secretSource)),
                        new ScenarioModelElement("known-door", "revelation", Map.of("value", "A door is behind the tapestry."), List.of(knownSource))),
                List.of(), List.of(), List.of(), "At the gate");
        var state = new StoryRuntimeState(1, UUID.randomUUID(), 1, "stage", 1, Map.of(),
                Map.of("known-door", RevelationStatus.LEARNED), Map.of(), null, false, Set.of());

        ScenarioModel projected = HiddenScenarioFacts.withoutUnrevealedRevelations(model, state);

        assertThat(projected.actors()).isEqualTo(model.actors());
        assertThat(projected.revelations()).containsExactly(model.revelations().get(1));
        assertThat(HiddenScenarioFacts.unrevealedRevelationValues(model, state))
                .containsExactly("The caretaker is the missing heir.");
        assertThat(HiddenScenarioFacts.unrevealedRevelationSourceRefs(model, state)).containsExactlyInAnyOrder(secretSource);
        assertThat(HiddenScenarioFacts.unrevealedRevelationIds(model, state)).containsExactly("hidden-heir");
    }

    @Test
    void only_the_descriptive_value_field_is_treated_as_hidden_text() {
        var model = new ScenarioModel(1, List.of(), List.of(), List.of(), List.of(
                new ScenarioModelElement("hidden-heir", "revelation", Map.of(
                        "value", "The caretaker is the missing heir.", "status", "UNKNOWN", "shortCode", "R-17"), List.of())),
                List.of(), List.of(), List.of(), "starting situation");

        assertThat(HiddenScenarioFacts.unrevealedRevelationValues(model, null))
                .containsExactly("The caretaker is the missing heir.");
    }
}
