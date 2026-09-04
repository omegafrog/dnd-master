package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleDocumentRole;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleDocumentSelection;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleId;
import com.dndmaster.adventure.domain.scenario.ScenarioCompilationDiagnostic;
import com.dndmaster.adventure.domain.scenario.ScenarioCompilationInputSnapshot;
import com.dndmaster.adventure.domain.scenario.ScenarioCreativity;
import com.dndmaster.adventure.domain.scenario.ScenarioModel;
import com.dndmaster.adventure.domain.scenario.ScenarioModelCompilationPolicy;
import com.dndmaster.adventure.domain.scenario.ScenarioModelElement;
import com.dndmaster.adventure.domain.scenario.ScenarioSourceReference;
import com.dndmaster.adventure.application.knowledge.KnowledgeDocumentStatus;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ScenarioModelCompilationPolicyTest {
    @Test
    void selectsSingleStorybookAsPrimaryAndRequiresExplicitPrimaryForMultiple() {
        UUID one = UUID.randomUUID();
        UUID two = UUID.randomUUID();
        ScenarioCompilationInputSnapshot single = ScenarioCompilationInputSnapshot.capture(
                new ScenarioBundleId(UUID.randomUUID()), 3, List.of(
                        storybook(one), storybook(UUID.randomUUID(), ScenarioBundleDocumentRole.RULEBOOK, "RULEBOOK")),
                null, null, ScenarioCreativity.CONSERVATIVE);
        ScenarioCompilationInputSnapshot multiple = ScenarioCompilationInputSnapshot.capture(
                new ScenarioBundleId(UUID.randomUUID()), 3, List.of(storybook(one), storybook(two)),
                null, null, ScenarioCreativity.CONSERVATIVE);

        assertEquals(one, single.primaryStorybookId());
        assertTrue(multiple.validate().stream().anyMatch(diagnostic -> diagnostic.code().equals("PRIMARY_STORYBOOK_REQUIRED")));
    }

    @Test
    void appliesIntegrationPromptThenPrimaryThenSupplementPrecedence() {
        UUID primary = UUID.randomUUID();
        ScenarioCompilationInputSnapshot snapshot = snapshot(primary, ScenarioCreativity.CONSERVATIVE);
        ScenarioModelElement integration = sourceElement("integration", "INTEGRATION_PROMPT", "prompt");
        ScenarioModelElement primaryElement = sourceElement("primary", "PRIMARY", "storybook");
        ScenarioModelElement supplement = sourceElement("supplement", "SUPPLEMENT", "storybook");

        var selected = ScenarioModelCompilationPolicy.resolveObjective(snapshot,
                List.of(integration, primaryElement, supplement));

        assertEquals("prompt", selected.selected().attributes().get("value"));
        assertEquals(List.of("primary", "supplement"), selected.discardedSourceIds());
    }

    @Test
    void creativityNoneBlocksMissingCoreResolutionAndNeverAutoPromotes() {
        ScenarioCompilationInputSnapshot snapshot = snapshot(UUID.randomUUID(), ScenarioCreativity.NONE);

        var result = ScenarioModelCompilationPolicy.evaluate(snapshot, ScenarioModel.empty());

        assertEquals(ScenarioModelCompilationPolicy.Status.BLOCKED, result.status());
        assertTrue(result.diagnostics().stream().map(ScenarioCompilationDiagnostic::code)
                .anyMatch("CORE_RESOLUTION_MISSING"::equals));
    }

    @Test
    void readyRequiresStorybookPrimaryAndCoreResolutionInformation() {
        UUID primary = UUID.randomUUID();
        ScenarioCompilationInputSnapshot snapshot = snapshot(primary, ScenarioCreativity.NONE);
        ScenarioModel model = new ScenarioModel(1,
                List.of(), List.of(),
                List.of(element("objective", "obj", "reach the tower")),
                List.of(), List.of(), List.of(),
                List.of(element("resolution", "resolve", "reach the tower")),
                "The party stands at the road.");

        var result = ScenarioModelCompilationPolicy.evaluate(snapshot, model);

        assertEquals(ScenarioModelCompilationPolicy.Status.READY, result.status());
        assertTrue(result.diagnostics().isEmpty());
    }

    private static ScenarioCompilationInputSnapshot snapshot(UUID primary, ScenarioCreativity creativity) {
        return ScenarioCompilationInputSnapshot.capture(new ScenarioBundleId(UUID.randomUUID()), 1,
                List.of(storybook(primary)), primary, "resolve conflicts using the integration instruction", creativity);
    }

    private static ScenarioBundleDocumentSelection storybook(UUID id) {
        return storybook(id, ScenarioBundleDocumentRole.MAIN_SCENARIO, "STORYBOOK");
    }

    private static ScenarioBundleDocumentSelection storybook(UUID id, ScenarioBundleDocumentRole role, String type) {
        return new ScenarioBundleDocumentSelection(new KnowledgeDocumentId(id), role,
                KnowledgeDocumentStatus.INDEXED, "source.txt", type, 1);
    }

    private static ScenarioModelElement element(String type, String id, String value) {
        return new ScenarioModelElement(id, type, Map.of("value", value), List.of(
                new ScenarioSourceReference(new KnowledgeDocumentId(UUID.randomUUID()), 1, "page:1")));
    }

    private static ScenarioModelElement sourceElement(String id, String source, String value) {
        return new ScenarioModelElement(id, "objective", Map.of("source", source, "value", value), List.of());
    }
}
