package com.dndmaster.ruleknowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndmaster.ruleknowledge.domain.evidence.EvidenceEdgeType;
import com.dndmaster.ruleknowledge.domain.evidence.EvidenceKind;
import com.dndmaster.ruleknowledge.domain.evidence.RuleEvidenceProjection;
import com.dndmaster.ruleknowledge.domain.evidence.RuleEvidenceProjector;
import com.dndmaster.ruleknowledge.domain.evidence.AncestorExpansionPolicy;
import com.dndmaster.ruleknowledge.domain.extraction.DocumentNode;
import com.dndmaster.ruleknowledge.domain.extraction.DocumentNodeType;
import com.dndmaster.ruleknowledge.domain.rulebook.RulebookId;
import java.util.List;
import org.junit.jupiter.api.Test;

class RuleEvidenceProjectorTest {
    @Test
    void projectsPerceptionLeafAndAbilityCheckAncestorsWithSourceSpans() {
        DocumentNode root = new DocumentNode("root", DocumentNodeType.ROOT, 1, null, "", List.of(
                new DocumentNode("ability", DocumentNodeType.HEADING, 1, null, "Ability Checks", List.of(
                        new DocumentNode("perception", DocumentNodeType.PARAGRAPH, 1, null,
                                "Perception. Wisdom (Perception) check", List.of(), List.of())), List.of())), List.of());

        RuleEvidenceProjection projection = new RuleEvidenceProjector().project(
                new RulebookId(java.util.UUID.randomUUID()), 3L, root);

        var perception = projection.units().stream()
                .filter(unit -> unit.content().startsWith("Perception."))
                .findFirst().orElseThrow();
        assertEquals(EvidenceKind.RULE, perception.kind());
        assertTrue(projection.edges().stream().anyMatch(edge ->
                edge.from().equals(perception.id()) && edge.type() == EvidenceEdgeType.PARENT));
        assertTrue(projection.units().stream().allMatch(unit -> !unit.sourceSpans().isEmpty()));
        assertTrue(projection.edges().stream().allMatch(edge -> !edge.sourceSpans().isEmpty()));
    }

    @Test
    void expandsOnlyAncestorsWithinTokenBudget() {
        RuleEvidenceProjection projection = RuleEvidenceProjection.fixtureWithAncestorChain(5);
        var leaf = projection.units().getLast();

        var expanded = new AncestorExpansionPolicy(6).expand(leaf.id(), projection, 6);

        assertEquals(3, expanded.size());
        assertEquals(leaf.id(), expanded.getFirst().id());
    }

    @Test
    void unknownNodesAreFailClosed() {
        DocumentNode raw = new DocumentNode("raw", DocumentNodeType.UNKNOWN, 1, null, "unclassified", List.of(), List.of());
        var projection = new RuleEvidenceProjector().project(RulebookId.generate(), 1L, raw);
        assertEquals(com.dndmaster.ruleknowledge.domain.evidence.EvidenceVisibility.UNKNOWN,
                projection.units().getFirst().visibility());
        assertTrue(!projection.units().getFirst().visibility().canExposeToPlayer());
    }

    @Test
    void rejectsBudgetBelowConfiguredMinimum() {
        var projection = RuleEvidenceProjection.fixtureWithAncestorChain(1);
        assertThrows(IllegalArgumentException.class,
                () -> new AncestorExpansionPolicy(10).expand(projection.units().getFirst().id(), projection, 9));
    }
}
