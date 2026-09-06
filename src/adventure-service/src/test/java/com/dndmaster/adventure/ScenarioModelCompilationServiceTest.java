package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dndmaster.adventure.application.scenario.compilation.ResolutionExtractionPort;
import com.dndmaster.adventure.application.scenario.compilation.ScenarioModelCompilationService;
import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleDocumentRole;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleDocumentSelection;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleId;
import com.dndmaster.adventure.domain.scenario.ScenarioCompilationInputSnapshot;
import com.dndmaster.adventure.domain.scenario.ScenarioCreativity;
import com.dndmaster.adventure.application.knowledge.KnowledgeDocumentStatus;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ScenarioModelCompilationServiceTest {
    @Test
    void leaves_hostile_encounters_for_the_runtime_gm_to_create_from_current_evidence() {
        UUID storybookId = UUID.randomUUID();
        var input = ScenarioCompilationInputSnapshot.capture(new ScenarioBundleId(UUID.randomUUID()), 1,
                List.of(new ScenarioBundleDocumentSelection(new KnowledgeDocumentId(storybookId),
                        ScenarioBundleDocumentRole.MAIN_SCENARIO, KnowledgeDocumentStatus.INDEXED, "brew.pdf",
                        "STORYBOOK", 1)), storybookId, null, ScenarioCreativity.CONSERVATIVE);
        var excerpt = new ResolutionExtractionPort.SourceExcerpt(new KnowledgeDocumentId(storybookId), 1,
                "page:2", "The objective is to clear the cellar. Eight Giant Rats are lurking in the cellar. "
                        + "If the party misses them, the Giant Rats begin combat.");

        var result = new ScenarioModelCompilationService().compile(input, List.of(), List.of(excerpt));

        assertEquals(List.of(), result.model().combatScenarios());
    }
}
