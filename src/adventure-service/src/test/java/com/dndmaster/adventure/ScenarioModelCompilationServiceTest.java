package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.adventure.application.scenario.compilation.ResolutionExtractionPort;
import com.dndmaster.adventure.application.scenario.compilation.ScenarioModelCompilationService;
import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleDocumentRole;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleDocumentSelection;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleId;
import com.dndmaster.adventure.domain.scenario.ScenarioCompilationInputSnapshot;
import com.dndmaster.adventure.domain.scenario.ScenarioModel;
import com.dndmaster.adventure.domain.scenario.ScenarioModelElement;
import com.dndmaster.adventure.domain.scenario.ScenarioModelCompilationPolicy;
import com.dndmaster.adventure.domain.scenario.ScenarioSourceReference;
import com.dndmaster.adventure.domain.scenario.ScenarioCreativity;
import com.dndmaster.adventure.application.knowledge.KnowledgeDocumentStatus;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ScenarioModelCompilationServiceTest {
    @Test
    void compiles_storybook_supported_combat_encounters_into_the_scenario_model() {
        UUID storybookId = UUID.randomUUID();
        var input = ScenarioCompilationInputSnapshot.capture(new ScenarioBundleId(UUID.randomUUID()), 1,
                List.of(new ScenarioBundleDocumentSelection(new KnowledgeDocumentId(storybookId),
                        ScenarioBundleDocumentRole.MAIN_SCENARIO, KnowledgeDocumentStatus.INDEXED, "brew.pdf",
                        "STORYBOOK", 1)), storybookId, null, ScenarioCreativity.CONSERVATIVE);
        var excerpt = new ResolutionExtractionPort.SourceExcerpt(new KnowledgeDocumentId(storybookId), 1,
                "page:2", "The objective is to clear the cellar. Eight Giant Rats are lurking in the cellar. "
                        + "If the party misses them, the Giant Rats begin combat.");

        var extractedEncounter = new ScenarioModelElement("giant-rats-cellar", "combat-scenario", java.util.Map.of(
                "enemyKey", "giant-rats", "displayName", "Giant Rats", "count", 8, "location", "cellar"),
                List.of(new ScenarioSourceReference(new KnowledgeDocumentId(storybookId), 1, excerpt.locator())));
        var extracted = new ScenarioModel(1, List.of(), List.of(), List.of(), List.of(), List.of(extractedEncounter),
                List.of(), List.of(), "");

        var result = new ScenarioModelCompilationService().compile(input, List.of(), List.of(excerpt), extracted);

        var encounter = result.model().combatScenarios().getFirst();
        assertEquals("giant-rats-cellar", encounter.scenarioId());
        assertEquals("giant-rats", encounter.enemyKey());
        assertEquals("Giant Rats", encounter.displayName());
        assertEquals(8, encounter.count());
        assertEquals("cellar", encounter.location());
        assertEquals(excerpt.locator(), encounter.sourceRefs().getFirst().locator());
        assertTrue(result.model().containsElement(encounter.scenarioId()));
    }

    @Test
    void blocks_compilation_instead_of_silently_dropping_an_encounter_with_an_unpublished_source() {
        UUID storybookId = UUID.randomUUID();
        var input = ScenarioCompilationInputSnapshot.capture(new ScenarioBundleId(UUID.randomUUID()), 1,
                List.of(new ScenarioBundleDocumentSelection(new KnowledgeDocumentId(storybookId),
                        ScenarioBundleDocumentRole.MAIN_SCENARIO, KnowledgeDocumentStatus.INDEXED, "brew.pdf",
                        "STORYBOOK", 1)), storybookId, null, ScenarioCreativity.CONSERVATIVE);
        var excerpt = new ResolutionExtractionPort.SourceExcerpt(new KnowledgeDocumentId(storybookId), 1,
                "page:2", "The giant rats begin combat.");
        var unsupported = new ScenarioModelElement("unsupported-rats", "combat-scenario", java.util.Map.of(
                "enemyKey", "giant-rat", "displayName", "Giant Rats", "count", 2),
                List.of(new ScenarioSourceReference(new KnowledgeDocumentId(UUID.randomUUID()), 1, "page:9")));
        var extracted = new ScenarioModel(1, List.of(), List.of(), List.of(), List.of(), List.of(unsupported),
                List.of(), List.of(), "");

        var result = new ScenarioModelCompilationService().compile(input, List.of(), List.of(excerpt), extracted);

        assertEquals(ScenarioModelCompilationPolicy.Status.BLOCKED, result.status());
        assertTrue(result.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals("COMBAT_SCENARIO_SOURCE_INVALID")));
    }
}
