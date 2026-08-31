package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.adventure.application.storyplan.AdventureStoryPlanProjectionDependencyPolicy;
import com.dndmaster.adventure.application.storyplan.AdventureStoryPlanProjectionRepairPolicy;
import com.dndmaster.adventure.application.storyplan.AdventureStoryPlanProjectionViolation;
import com.dndmaster.adventure.application.storyplan.AdventureStoryPlanProjectionViolation.Repairability;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class AdventureStoryPlanProjectionDependencyPolicyTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void computes_explicit_same_stage_dependency_closure_for_a_participant_blocker() {
        var blocker = violation("stages[0].combatSkeleton.participants[0].name");

        var scope = AdventureStoryPlanProjectionDependencyPolicy.scope(
                "{\"stages\":[{\"position\":1}]}", List.of(blocker));

        assertTrue(scope.blockerPaths().contains("stages[0].combatSkeleton.participants[0].name"));
        assertTrue(scope.dependentPaths().contains("stages[0].combatRequirement"));
        assertTrue(scope.dependentPaths().contains("stages[0].combatSkeleton.participants[0].citationKeys"));
        assertTrue(scope.dependentPaths().contains("stages[0].evidence[*].citationKey"));
        assertTrue(scope.allows("stages[0].combatSkeleton.participants[0].name"));
        assertFalse(scope.allows("stages[0].combatSkeleton.objective"));
        assertFalse(scope.allows("stages[1].combatSkeleton.objective"));
    }

    @Test
    void diff_guard_accepts_only_dependency_closure_and_preserves_unrelated_stage() throws Exception {
        var previous = mapper.readTree("""
                {"stages":[
                  {"combatRequirement":"REQUIRED","combatSkeleton":{"participants":[{"name":"쥐"}],"objective":"퇴치","successOutcome":"성공","failureOutcome":"후퇴"},"sourceFactClaims":[],"tacticalPreparationRequirement":"REQUIRED","title":"고정"},
                  {"title":"검증된 사실","goal":"보존"}
                ]}
                """);
        var repaired = mapper.readTree("""
                {"stages":[
                  {"combatRequirement":"REQUIRED","combatSkeleton":{"participants":[{"name":"거대 쥐","citationKeys":["rat"]}],"objective":"거대 쥐를 퇴치","successOutcome":"성공","failureOutcome":"후퇴"},"sourceFactClaims":[{"fieldPath":"combatSkeleton.participants[0].name"}],"tacticalPreparationRequirement":"REQUIRED","title":"고정"},
                  {"title":"검증된 사실","goal":"보존"}
                ]}
                """);
        var scope = AdventureStoryPlanProjectionDependencyPolicy.scope(previous.toString(),
                List.of(violation("stages[0].combatSkeleton.participants[0].name")));

        org.junit.jupiter.api.Assertions.assertThrows(AdventureStoryPlanProjectionRepairPolicy.UnlistedFieldMutation.class,
                () -> AdventureStoryPlanProjectionRepairPolicy.assertOnlyListedFieldsChanged(previous, repaired, scope));
    }

    @Test
    void diff_guard_rejects_mutation_outside_dependency_closure_in_another_stage() throws Exception {
        var previous = mapper.readTree("""
                {"stages":[{"combatSkeleton":{"participants":[{"name":"쥐"}]}},{"title":"고정"}]}
                """);
        var repaired = mapper.readTree("""
                {"stages":[{"combatSkeleton":{"participants":[{"name":"거대 쥐"}]}},{"title":"변조"}]}
                """);
        var scope = AdventureStoryPlanProjectionDependencyPolicy.scope(previous.toString(),
                List.of(violation("stages[0].combatSkeleton.participants[0].name")));

        org.junit.jupiter.api.Assertions.assertThrows(AdventureStoryPlanProjectionRepairPolicy.UnlistedFieldMutation.class,
                () -> AdventureStoryPlanProjectionRepairPolicy.assertOnlyListedFieldsChanged(previous, repaired, scope));
    }

    private static AdventureStoryPlanProjectionViolation violation(String path) {
        return new AdventureStoryPlanProjectionViolation("COMBAT_PARTICIPANT_SOURCE_REQUIRED", 1, path,
                "", "authoritative field evidence", Repairability.REPAIRABLE, "participant evidence is required");
    }
}
