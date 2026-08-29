package com.dndmaster.adventure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dndmaster.adventure.application.runtime.EvidencePack;
import com.dndmaster.adventure.application.runtime.GmAgentPort;
import com.dndmaster.adventure.application.runtime.GmAgentRuntimePlanningAdapter;
import com.dndmaster.adventure.application.runtime.GmFinalValidator;
import com.dndmaster.adventure.application.runtime.GmPlanResult;
import com.dndmaster.adventure.application.runtime.RuntimePlan;
import com.dndmaster.adventure.application.runtime.RuntimePlanningRequest;
import com.dndmaster.adventure.domain.adventure.AdventureContext;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GmAgentRuntimePlanningAdapterTest {
    @Test
    void translates_only_explicit_change_and_reveal_delta_semantics() {
        RuntimePlan plan = plan(List.of("change:door", "reveal:secret"));
        var translated = new GmAgentRuntimePlanningAdapter(
                context -> new GmPlanResult(plan, "provider", "model", "reasoning", List.of("change:door", "reveal:secret")),
                new GmFinalValidator()).plan(request());

        assertThat(translated.stateDelta().changedFactIds()).containsExactly("door");
        assertThat(translated.stateDelta().revealedFactIds()).containsExactly("secret");
    }

    @Test
    void rejects_unsupported_raw_delta_instead_of_broadening_it_into_a_reveal() {
        RuntimePlan plan = plan(List.of("door"));

        assertThatThrownBy(() -> new GmAgentRuntimePlanningAdapter(
                context -> new GmPlanResult(plan, "provider", "model", "reasoning", List.of("door")),
                new GmFinalValidator()).plan(request()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsupported state delta");
    }

    private static RuntimePlanningRequest request() {
        return new RuntimePlanningRequest(AdventureId.generate(), new OwnerPlayerId(UUID.randomUUID()),
                UUID.randomUUID(), 1, new AdventureContext("scene", null, null, null), null,
                "action", new EvidencePack(List.of(), List.of(), List.of()));
    }

    private static RuntimePlan plan(List<String> ignored) {
        return new RuntimePlan("scene", null, "judgment", "narration", null, List.of(), List.of());
    }
}
