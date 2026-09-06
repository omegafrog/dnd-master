package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import com.dndmaster.adventure.domain.scenario.DetailedStage;
import com.dndmaster.adventure.domain.scenario.FunnelDefinition;
import com.dndmaster.adventure.domain.scenario.PressureDefinition;
import com.dndmaster.adventure.domain.scenario.RevelationDefinition;
import com.dndmaster.adventure.domain.scenario.ScenarioSourceReference;
import com.dndmaster.adventure.domain.scenario.SituationDefinition;
import com.dndmaster.adventure.domain.scenario.StageIntent;
import com.dndmaster.adventure.domain.scenario.ThreatDefinition;
import com.dndmaster.adventure.domain.runtime.story.PressureOperation;
import com.dndmaster.adventure.domain.runtime.story.PressureProposal;
import com.dndmaster.adventure.domain.runtime.story.SituationAction;
import com.dndmaster.adventure.domain.runtime.story.StoryRuntimeRules;
import com.dndmaster.adventure.domain.runtime.story.StoryRuntimeProposal;
import com.dndmaster.adventure.domain.runtime.story.StoryRuntimeState;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StoryRuntimeStateTest {
    private final UUID packageId = UUID.randomUUID();
    private final ScenarioSourceReference evidence = new ScenarioSourceReference(
            new KnowledgeDocumentId(UUID.randomUUID()), 1, "page:1:span:1");

    @Test
    void starts_with_opening_situation_and_can_return_to_open_play() {
        DetailedStage stage = stage();
        StoryRuntimeState started = StoryRuntimeState.start(stage);

        assertEquals("opening", started.activeSituationId());
        assertFalse(started.isOpenPlay());

        StoryRuntimeState openPlay = StoryRuntimeRules.apply(started, stage,
                proposal(SituationAction.finish("opening")));

        assertTrue(openPlay.isOpenPlay());
        assertEquals(com.dndmaster.adventure.domain.runtime.story.SituationStatus.USED,
                openPlay.situationStatuses().get("opening"));
    }

    @Test
    void learns_the_same_revelation_from_different_paths_only_once() {
        DetailedStage stage = stage();
        StoryRuntimeState initial = StoryRuntimeState.start(stage);
        StoryRuntimeState learned = StoryRuntimeRules.apply(initial, stage,
                proposal(List.of("truth"), List.of()));
        StoryRuntimeState duplicate = StoryRuntimeRules.apply(learned, stage,
                proposal(List.of("truth"), List.of()).withExpectedVersion(learned.version()));

        assertEquals(1, learned.learnedRevelationIds().size());
        assertEquals(learned.version(), duplicate.version());
    }

    @Test
    void pressure_is_ai_timed_and_runtime_rejects_invalid_or_stale_proposals() {
        DetailedStage stage = stage();
        StoryRuntimeState initial = StoryRuntimeState.start(stage);
        StoryRuntimeProposal advance = proposal(List.of(), List.of(
                new PressureProposal("pressure", PressureOperation.ADVANCE, 0, null)));
        StoryRuntimeState progressed = StoryRuntimeRules.apply(initial, stage, advance);

        assertEquals(1, progressed.pressureStates().get("pressure").progression());
        assertThrows(IllegalStateException.class,
                () -> StoryRuntimeRules.apply(initial, stage, advance.withExpectedVersion(99)));
        assertThrows(IllegalStateException.class,
                () -> StoryRuntimeRules.apply(progressed, stage,
                proposal(List.of(), List.of(new PressureProposal("pressure", PressureOperation.ADVANCE, 0, null)))
                                .withExpectedVersion(progressed.version())));
    }

    @Test
    void pressure_can_be_skipped_cancelled_or_replaced_without_a_turn_timer() {
        DetailedStage stage = stage();
        StoryRuntimeState skipped = StoryRuntimeRules.apply(StoryRuntimeState.start(stage), stage,
                proposal(List.of(), List.of(new PressureProposal("pressure", PressureOperation.SKIP, 0, null))));
        StoryRuntimeState cancelled = StoryRuntimeRules.apply(StoryRuntimeState.start(stage), stage,
                proposal(List.of(), List.of(new PressureProposal("pressure", PressureOperation.CANCEL, 0, null))));
        StoryRuntimeState replaced = StoryRuntimeRules.apply(StoryRuntimeState.start(stage), stage,
                proposal(List.of(), List.of(new PressureProposal("pressure", PressureOperation.REPLACE, 0, "The threat changes course"))));

        assertEquals(com.dndmaster.adventure.domain.runtime.story.PressureStatus.SKIPPED,
                skipped.pressureStates().get("pressure").status());
        assertEquals(com.dndmaster.adventure.domain.runtime.story.PressureStatus.CANCELLED,
                cancelled.pressureStates().get("pressure").status());
        assertEquals(com.dndmaster.adventure.domain.runtime.story.PressureStatus.REPLACED,
                replaced.pressureStates().get("pressure").status());
        assertEquals("The threat changes course", replaced.pressureStates().get("pressure").material());
    }

    @Test
    void rejects_situation_without_current_stage_intent_target() {
        DetailedStage stage = stage();
        assertThrows(IllegalArgumentException.class, () -> new SituationDefinition(
                "ambient", List.of(StageIntent.REVELATION), List.of(evidence)));
        assertEquals(packageId, stage.scenarioPackageId());
    }

    @Test
    void preserves_runtime_progression_through_json_round_trip() throws Exception {
        DetailedStage stage = stage();
        StoryRuntimeState initial = StoryRuntimeState.start(stage);
        StoryRuntimeState progressed = StoryRuntimeRules.apply(initial, stage,
                proposal(List.of("truth"), List.of(
                        new PressureProposal("pressure", PressureOperation.ADVANCE, 0, null))));

        String json = new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules().writeValueAsString(progressed);
        StoryRuntimeState restored = new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules()
                .readValue(json, StoryRuntimeState.class);

        assertEquals(progressed.version(), restored.version());
        assertEquals(progressed.learnedRevelationIds(), restored.learnedRevelationIds());
        assertEquals(progressed.pressureStates(), restored.pressureStates());
    }

    private StoryRuntimeProposal proposal(SituationAction action) {
        return proposal(action, List.of(), List.of());
    }

    private StoryRuntimeProposal proposal(List<String> revelations, List<PressureProposal> pressures) {
        return proposal(SituationAction.none(), revelations, pressures);
    }

    private StoryRuntimeProposal proposal(SituationAction action, List<String> revelations,
            List<PressureProposal> pressures) {
        return new StoryRuntimeProposal(UUID.randomUUID(), 0, packageId, 1, "stage-1", 1,
                action, revelations, pressures);
    }

    private DetailedStage stage() {
        return new DetailedStage(packageId, 1, "stage-1", 1, "Find the key",
                List.of(new RevelationDefinition("truth", true, List.of(evidence))),
                new ThreatDefinition("The door is guarded", List.of(evidence)),
                new PressureDefinition("The search grows dangerous", List.of(evidence)),
                new FunnelDefinition("The key is found", List.of("truth"), List.of(evidence)),
                List.of(new SituationDefinition("opening", List.of(StageIntent.REVELATION), List.of("truth"),
                        List.of(), List.of(), List.of(), List.of(), true, List.of(evidence)),
                        new SituationDefinition("pressure-scene", List.of(StageIntent.PRESSURE), List.of(),
                                List.of("pressure"), List.of(), List.of(), List.of(), false, List.of(evidence))),
                List.of(), List.of(evidence));
    }
}
