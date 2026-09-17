package com.dndmaster.combatmap;

import static org.junit.jupiter.api.Assertions.*;

import com.dndmaster.combatmap.application.spatial.SpatialFeaturePlacementModelPort;
import com.dndmaster.combatmap.application.spatial.SpatialFeaturePreparationInput;
import com.dndmaster.combatmap.application.spatial.SpatialFeaturePlacementProposal;
import com.dndmaster.combatmap.application.spatial.SpatialFeaturePreparationService;
import com.dndmaster.combatmap.domain.*;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SpatialFeaturePreparationTest {
    private static final UUID FEATURE_ID = UUID.randomUUID();

    @Test
    void stores_feature_state_with_fixed_cells_and_allows_overlapping_magic_effects() {
        SpatialFeature trap = SpatialFeature.hidden(
                FEATURE_ID,
                SpatialFeatureType.TRAP,
                Set.of(new GridPosition(2, 2)),
                DetectionSpec.passive("perception", 12),
                Set.of(SpatialTrigger.ENTER_CELL),
                SpatialFeatureProvenance.storyPlan("story-plan:trap-1", 1, 0));
        SpatialFeature magic = SpatialFeature.active(
                UUID.randomUUID(),
                SpatialFeatureType.MAGICAL_AREA_EFFECT,
                Set.of(new GridPosition(2, 2), new GridPosition(2, 3)),
                SpatialFeatureProvenance.runtime("spell:fire-wall", 2, 1),
                3);

        assertEquals(SpatialFeatureVisibility.HIDDEN, trap.visibility());
        assertEquals(SpatialFeature.State.HIDDEN, trap.state());
        assertEquals(Set.of(new GridPosition(2, 2)), trap.cells());
        assertTrue(magic.cells().contains(new GridPosition(2, 2)));
        assertEquals(SpatialFeature.State.ACTIVE, magic.state());
        assertEquals(3, magic.remainingDurationTurns());

        trap.discover();
        assertEquals(SpatialFeatureVisibility.DISCOVERED, trap.visibility());
        assertEquals(SpatialFeature.State.DISCOVERED, trap.state());
        trap.trigger();
        assertEquals(SpatialFeature.State.TRIGGERED, trap.state());
    }

    @Test
    void rejects_retroactive_hidden_story_plan_feature_without_mutating_the_map() {
        CombatMap map = map();
        map.replaceVisibility(new VisibilitySnapshot(
                Set.of(new GridPosition(0, 0)),
                Set.of(new GridPosition(0, 0), new GridPosition(1, 1)),
                Set.of(), List.of(), 1));
        SpatialFeature hidden = SpatialFeature.hidden(
                FEATURE_ID,
                SpatialFeatureType.SECRET_DOOR,
                Set.of(new GridPosition(1, 1)),
                DetectionSpec.passive("investigation", 10),
                Set.of(SpatialTrigger.OBSERVE),
                SpatialFeatureProvenance.storyPlan("story-plan:door-1", 1, 0));

        assertThrows(IllegalArgumentException.class, () -> map.materializeSpatialFeatures(List.of(hidden)));
        assertTrue(map.spatialFeatures().isEmpty());
    }

    @Test
    void retries_a_candidate_twice_then_blocks_required_and_omits_optional() {
        CombatMap map = map();
        UUID optionalId = UUID.randomUUID();
        SpatialFeaturePlacementModelPort model = context -> new SpatialFeaturePlacementProposal(List.of(
                new SpatialFeaturePlacementProposal.Candidate(
                        FEATURE_ID, SpatialFeatureType.TRAP, List.of(), true, "story-plan:trap"),
                new SpatialFeaturePlacementProposal.Candidate(
                        optionalId, SpatialFeatureType.SECRET_DOOR, List.of(), false, "story-plan:door")));

        SpatialFeaturePreparationService.Result result = new SpatialFeaturePreparationService(model)
                .prepare(map, "story-plan-1", 1);

        assertEquals(3, result.attempts());
        assertFalse(result.activationAllowed());
        assertTrue(result.map().spatialPreparationBlocked());
        assertTrue(result.map().spatialFeatures().isEmpty());
        assertTrue(result.failures().stream().anyMatch(message -> message.contains(FEATURE_ID.toString())));
        assertTrue(result.warnings().stream().anyMatch(message -> message.contains(optionalId.toString())));
    }

    @Test
    void materializes_a_valid_batch_after_one_evidence_based_attempt() {
        CombatMap map = map();
        SpatialFeaturePlacementModelPort model = context -> new SpatialFeaturePlacementProposal(List.of(
                new SpatialFeaturePlacementProposal.Candidate(
                        FEATURE_ID, SpatialFeatureType.HAZARD_AREA,
                        List.of(new GridPosition(2, 2)), true, "published-story-plan:hazard-1")));

        SpatialFeaturePreparationService.Result result = new SpatialFeaturePreparationService(model)
                .prepare(map, "story-plan-1", 1);

        assertTrue(result.activationAllowed());
        assertFalse(result.map().spatialPreparationBlocked());
        assertEquals(1, result.attempts());
        assertEquals(Set.of(FEATURE_ID), result.map().spatialFeatures().stream().map(SpatialFeature::id).collect(java.util.stream.Collectors.toSet()));
        assertEquals(SpatialFeatureOrigin.STORY_PLAN, result.map().spatialFeatures().getFirst().provenance().origin());
    }

    @Test
    void validates_ai_cells_and_evidence_against_authoritative_preparation_input() {
        CombatMap map = map();
        SpatialFeaturePreparationInput input = new SpatialFeaturePreparationInput(
                "story-plan:published-1",
                List.of(new SpatialFeaturePreparationInput.Requirement(
                        FEATURE_ID, SpatialFeatureType.TRAP, true, Set.of("storybook:page-4"),
                        DetectionSpec.passive("perception", 12), Set.of(SpatialTrigger.ENTER_CELL))));
        SpatialFeaturePlacementModelPort model = context -> new SpatialFeaturePlacementProposal(List.of(
                new SpatialFeaturePlacementProposal.Candidate(
                        FEATURE_ID, SpatialFeatureType.TRAP, List.of(new GridPosition(2, 2)), true,
                        "invented-coordinate-evidence")));

        SpatialFeaturePreparationService.Result result = new SpatialFeaturePreparationService(model)
                .prepare(map, input, 1);

        assertFalse(result.activationAllowed());
        assertTrue(result.map().spatialPreparationBlocked());
        assertTrue(result.failures().stream().anyMatch(message -> message.contains("evidence")));
        assertTrue(result.map().spatialFeatures().isEmpty());
    }

    private static CombatMap map() {
        return new CombatMap(
                new MapId(UUID.randomUUID()), new AdventureId(UUID.randomUUID()), new RuleSetId(UUID.randomUUID()),
                new GridSpec(5, 5, 50, 5), List.of(), Set.of(), List.of());
    }
}
