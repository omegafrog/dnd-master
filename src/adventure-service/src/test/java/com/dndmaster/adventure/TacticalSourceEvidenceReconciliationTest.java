package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.adventure.application.storyplan.AdventureStoryPlanGenerationPort;
import com.dndmaster.adventure.application.storyplan.SourceEvidenceReconciliationPort;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import com.dndmaster.adventure.domain.adventure.*;

class TacticalSourceEvidenceReconciliationTest {
    @Test
    void rejectsFabricatedQuoteAtAnOtherwiseValidDocumentAndLocator() {
        var documentId = UUID.randomUUID();
        var authoritative = new AdventureStoryPlanGenerationPort.SourceCitation("STORYBOOK", documentId, 7,
                "page:1:span:2", "The cellar contains a rat swarm.", 1.0);
        var fabricated = new AdventureStoryPlanGenerationPort.SourceCitation("STORYBOOK", documentId, 7,
                "page:1:span:2", "The cellar contains a dragon.", 1.0);

        var violations = SourceEvidenceReconciliationPort.exact().reconcile(List.of(authoritative), List.of(fabricated));

        assertTrue(violations.contains("tactical source evidence does not match the authoritative extraction"));
    }

    @Test
    void rejectsAnAiInferenceThatContradictsASourceFact() {
        var documentId = UUID.randomUUID();
        var source = new AdventureStoryPlanGenerationPort.SourceCitation("STORYBOOK", documentId, 7,
                "page:1", "The cellar contains a rat swarm.", 1.0);
        var scene = new TacticalScenePlan(1, TacticalScenePlanStatus.READY,
                new TacticalSceneBoundary(new NormalizedCoordinate(0, 0), new NormalizedCoordinate(1, 1), List.of()),
                List.of(new TacticalPlacement("hero", TacticalPlacementKind.PLAYER, new NormalizedCoordinate(.1, .1), PlacementGrounding.aiInference("party"))),
                List.of(), List.of(), List.of(), List.of(new TacticalPlacement("dragon", TacticalPlacementKind.BOSS, new NormalizedCoordinate(.8, .8), PlacementGrounding.aiInference("boss"))), List.of(), List.of(),
                new FogPlan(List.of(), PlacementGrounding.sourceCitation("STORYBOOK:" + documentId + ":page:1")),
                List.of(new TacticalTrigger("entry", TacticalTriggerType.COMBAT_ENTRY, List.of("dragon"), "", PlacementGrounding.aiInference("entry"))),
                List.of(new TacticalOutcome("escape", "treasure", PlacementGrounding.aiInference("outcome"))), List.of());

        var violations = SourceEvidenceReconciliationPort.exact().reconcile(List.of(source), List.of(source), scene);

        assertTrue(violations.stream().anyMatch(value -> value.contains("contradicts authoritative source")));
    }
}
