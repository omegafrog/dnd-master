package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.adventure.application.scenario.blueprint.CharacterCreationBlueprintCompiler;
import com.dndmaster.adventure.application.scenario.blueprint.CharacterCreationBlueprintCompiler.FieldCandidate;
import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import com.dndmaster.adventure.domain.scenario.CharacterInputNodeStatus;
import com.dndmaster.adventure.domain.scenario.InputMode;
import com.dndmaster.adventure.domain.scenario.ScenarioSourceReference;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CharacterInputTreeTest {
    private static final ScenarioSourceReference SOURCE = new ScenarioSourceReference(
            new KnowledgeDocumentId(UUID.randomUUID()), 1, "page:1");

    @Test
    void buildsStableParentChildTreeAndRepresentsPartialExtraction() {
        var blueprint = new CharacterCreationBlueprintCompiler().compile(1, List.of(
                new FieldCandidate("starting_ability_scores.str", List.of(), true, "STORYBOOK", SOURCE,
                        "STR", InputMode.FREE_TEXT, List.of("12"))));

        var root = blueprint.node("starting_ability_scores");
        var child = blueprint.node("starting_ability_scores.str");

        assertEquals(null, root.parentId());
        assertEquals(CharacterInputNodeStatus.PARTIALLY_EXTRACTED, root.status());
        assertTrue(root.allowUserAddChild());
        assertTrue(child.parentId() != null);
        assertTrue(!child.parentId().equals("starting_ability_scores"));
        assertEquals(InputMode.FREE_TEXT, child.inputMode());
        assertEquals(List.of("12"), child.suggestions());
    }

    @Test
    void addsChildAndResolvesValueWithoutChangingModeParentOrEvidence() {
        var blueprint = new CharacterCreationBlueprintCompiler().compile(1, List.of(
                new FieldCandidate("starting_ability_scores.str", List.of(), true, "STORYBOOK", SOURCE,
                        "STR", InputMode.FREE_TEXT, List.of())));

        var added = blueprint.addUserInputChild("starting_ability_scores", "con", "CON");
        var strNodeId = added.node("starting_ability_scores.str").id();
        var resolved = added.resolveNode(strNodeId, "12");

        assertEquals(2, added.revision());
        assertEquals(CharacterInputNodeStatus.USER_ADDED, added.node("starting_ability_scores.con").status());
        assertEquals("CON", added.node("starting_ability_scores.con").label());
        assertEquals(3, resolved.revision());
        assertEquals(strNodeId, resolved.node("starting_ability_scores.str").id());
        assertEquals(added.node("starting_ability_scores.str").parentId(), resolved.node("starting_ability_scores.str").parentId());
        assertEquals(InputMode.FREE_TEXT, resolved.node("starting_ability_scores.str").inputMode());
        assertEquals(SOURCE, resolved.node("starting_ability_scores.str").sourceEvidence().getFirst());
        assertEquals("12", resolved.node("starting_ability_scores.str").value());
    }
}
