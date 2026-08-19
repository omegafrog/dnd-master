package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.adventure.application.storyplan.AdventureStoryPlanGenerationPort;
import com.dndmaster.adventure.application.storyplan.SourceEvidenceReconciliationPort;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

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
}
