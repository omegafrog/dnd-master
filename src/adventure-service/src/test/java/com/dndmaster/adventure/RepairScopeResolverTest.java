package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.adventure.application.storyplan.AdventureStoryPlanProjectionViolation;
import com.dndmaster.adventure.application.storyplan.RepairScope;
import com.dndmaster.adventure.application.storyplan.RepairScopeResolver;
import java.util.List;
import org.junit.jupiter.api.Test;

class RepairScopeResolverTest {
    @Test
    void resolves_rule_and_outcome_paths_and_unions_dependencies() {
        var violations = List.of(
                violation("MISSING_RULE_CHECK", "stages[3].rules.check"),
                violation("MISSING_RULE_OUTCOME", "stages[3].rules.outcome"));

        RepairScope scope = new RepairScopeResolver().resolve("{\"stages\":[]}", violations);

        assertEquals(2, scope.blockerPaths().size());
        assertTrue(scope.allows("stages[3].rules.check"));
        assertTrue(scope.allows("stages[3].rules.outcome"));
        assertTrue(scope.isRepairable());
    }

    @Test
    void returns_unresolved_for_unknown_paths() {
        var result = new RepairScopeResolver().tryResolve("{\"stages\":[]}",
                List.of(violation("UNKNOWN", "candidate.mystery")));

        assertTrue(result.isEmpty());
    }

    @Test
    void expands_combat_participant_grounding_to_its_same_stage_dependencies() {
        var scope = new RepairScopeResolver().resolve("{\"stages\":[{}]}", List.of(
                violation("COMBAT_PARTICIPANT_SOURCE_UNSUPPORTED",
                        "stages[0].combatSkeleton.participants[0].name")));

        assertTrue(scope.allows("stages[0].combatSkeleton.participants[0].name"));
        assertTrue(scope.allows("stages[0].combatSkeleton.participants[0].citationKeys"));
        assertTrue(scope.allows("stages[0].evidence[0].citationKey"));
        assertTrue(!scope.allows("stages[0].combatSkeleton.objective"));
        assertTrue(!scope.allows("stages[0].sourceFactClaims[0]"));
        assertTrue(!scope.allows("stages[0].tacticalPreparationRequirement"));
        assertTrue(!scope.allows("stages[1].title"));
    }

    @Test
    void accepts_exact_stage_ending_ids_repair_without_expanding_other_fields() {
        var scope = new RepairScopeResolver().resolve("{\"stages\":[{}]}", List.of(
                violation("ENDING_IDS_MISSING", "stages[0].endingIds")));

        assertTrue(scope.isRepairable());
        assertTrue(scope.allows("stages[0].endingIds"));
        assertTrue(!scope.allows("stages[0].title"));
        assertTrue(!scope.allows("stages[1].endingIds"));
    }

    private static AdventureStoryPlanProjectionViolation violation(String code, String path) {
        return new AdventureStoryPlanProjectionViolation(code, 4, path, "", "",
                AdventureStoryPlanProjectionViolation.Repairability.REPAIRABLE, "test");
    }
}
