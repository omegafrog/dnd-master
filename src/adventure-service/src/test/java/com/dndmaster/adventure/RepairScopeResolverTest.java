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

    private static AdventureStoryPlanProjectionViolation violation(String code, String path) {
        return new AdventureStoryPlanProjectionViolation(code, 4, path, "", "",
                AdventureStoryPlanProjectionViolation.Repairability.REPAIRABLE, "test");
    }
}
