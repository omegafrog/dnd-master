package com.dndmaster.aigamemaster.domain.turnplan;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class TurnPlanTest {
    @Test
    void rejectsOverlappingInformationBoundaries() {
        InformationPolicy policy = new InformationPolicy(List.of("door.secret"), List.of(), List.of("door.secret"));
        TurnPlan plan = Fixtures.plan(policy);
        assertThrows(TurnPlanValidationException.class, () -> new TurnPlanValidator().validate(plan));
    }

    @Test
    void rejectsDuplicateFactsAndStoryConditions() {
        assertThrows(IllegalArgumentException.class,
                () -> new InformationPolicy(List.of("fact", "fact"), List.of(), List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new StoryProgress("stage-1", List.of("condition", "condition")));
    }

    static final class Fixtures {
        static TurnPlan plan(InformationPolicy policy) {
            return new TurnPlan("1", "turn-1", new PlayerIntent("observe", "inspect", List.of("mural")),
                    List.of(), new NarrativeIntent(ScenePurpose.EXPLORATION, NarrativeTone.MYSTERIOUS,
                    NarrativePacing.MODERATE), policy, List.of(), new StoryProgress("stage-1", List.of()));
        }
    }
}
