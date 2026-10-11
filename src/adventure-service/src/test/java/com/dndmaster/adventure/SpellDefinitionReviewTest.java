package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import com.dndmaster.adventure.domain.scenario.BasicRulebookSpellInventory;
import com.dndmaster.adventure.domain.scenario.StructuredSpellDefinition;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SpellDefinitionReviewTest {
    @Test
    void targetingSupportRequiresOnlyTheDimensionsUsedByItsShape() {
        assertFalse(targeting(StructuredSpellDefinition.EffectShape.CONE, 0, null, null, null,
                StructuredSpellDefinition.PathBlockingRule.NONE).supported());
        assertFalse(targeting(StructuredSpellDefinition.EffectShape.LINE, 30, null, null, null,
                StructuredSpellDefinition.PathBlockingRule.NONE).supported());
        assertFalse(targeting(StructuredSpellDefinition.EffectShape.SPHERE, null, null, null, null,
                StructuredSpellDefinition.PathBlockingRule.NONE).supported());
        assertFalse(targeting(StructuredSpellDefinition.EffectShape.CUBE, null, null, null, null,
                StructuredSpellDefinition.PathBlockingRule.NONE).supported());
        assertFalse(targeting(StructuredSpellDefinition.EffectShape.CYLINDER, null, null, null, 10,
                StructuredSpellDefinition.PathBlockingRule.NONE).supported());
        assertFalse(targeting(StructuredSpellDefinition.EffectShape.WALL, 20, null, 10, null,
                StructuredSpellDefinition.PathBlockingRule.NONE).supported());

        assertTrue(targeting(StructuredSpellDefinition.EffectShape.CONE, 15, null, null, null,
                StructuredSpellDefinition.PathBlockingRule.NONE).supported());
        assertTrue(targeting(StructuredSpellDefinition.EffectShape.LINE, 30, 5, null, null,
                StructuredSpellDefinition.PathBlockingRule.SOLID_BLOCKS).supported());
        assertTrue(targeting(StructuredSpellDefinition.EffectShape.SPHERE, null, null, null, 10,
                StructuredSpellDefinition.PathBlockingRule.CLEAR_PATH_REQUIRED).supported());
        assertTrue(targeting(StructuredSpellDefinition.EffectShape.CUBE, 15, null, null, null,
                StructuredSpellDefinition.PathBlockingRule.NONE).supported());
        assertTrue(targeting(StructuredSpellDefinition.EffectShape.CYLINDER, null, null, 20, 10,
                StructuredSpellDefinition.PathBlockingRule.NONE).supported());
        assertTrue(targeting(StructuredSpellDefinition.EffectShape.WALL, 20, 5, 10, null,
                StructuredSpellDefinition.PathBlockingRule.NONE).supported());
        assertTrue(targeting(StructuredSpellDefinition.EffectShape.NONE, null, null, null, null,
                StructuredSpellDefinition.PathBlockingRule.NONE).supported());
    }

    @Test
    void unsupportedPathRuleIsNotCollapsedIntoRepresentableRules() {
        var unsupported = targeting(StructuredSpellDefinition.EffectShape.NONE, null, null, null, null,
                StructuredSpellDefinition.PathBlockingRule.UNSUPPORTED);

        assertFalse(unsupported.supported());
        assertTrue(targeting(StructuredSpellDefinition.EffectShape.NONE, null, null, null, null,
                StructuredSpellDefinition.PathBlockingRule.SOLID_BLOCKS).supported());
        assertTrue(targeting(StructuredSpellDefinition.EffectShape.NONE, null, null, null, null,
                StructuredSpellDefinition.PathBlockingRule.CLEAR_PATH_REQUIRED).supported());
    }

    @Test
    void rangeSupportRequiresValuesThatMatchItsForm() {
        assertFalse(new StructuredSpellDefinition.RangeProfile(StructuredSpellDefinition.RangeForm.UNSUPPORTED, null, null).supported());
        assertFalse(new StructuredSpellDefinition.RangeProfile(StructuredSpellDefinition.RangeForm.SINGLE, 0, null).supported());
        assertTrue(new StructuredSpellDefinition.RangeProfile(StructuredSpellDefinition.RangeForm.SINGLE, 60, null).supported());
        assertFalse(new StructuredSpellDefinition.RangeProfile(StructuredSpellDefinition.RangeForm.NORMAL_AND_LONG, 60, 60).supported());
        assertTrue(new StructuredSpellDefinition.RangeProfile(StructuredSpellDefinition.RangeForm.NORMAL_AND_LONG, 60, 120).supported());
    }

    private static StructuredSpellDefinition.TargetingProfile targeting(StructuredSpellDefinition.EffectShape shape,
            Integer length, Integer width, Integer height, Integer radius,
            StructuredSpellDefinition.PathBlockingRule pathBlocking) {
        return new StructuredSpellDefinition.TargetingProfile(
                StructuredSpellDefinition.TargetMode.SINGLE_TARGET,
                StructuredSpellDefinition.OriginMode.ACTOR_TOKEN, shape, length, width, height, radius,
                pathBlocking, StructuredSpellDefinition.PropagationRule.DIRECT,
                shape == StructuredSpellDefinition.EffectShape.CYLINDER
                        ? StructuredSpellDefinition.VerticalDirection.UP : null);
    }

    @Test
    void editsKeepSourceAndOwnershipAndRequireApprovalAgain() {
        var source = BasicRulebookSpellInventory.load(new KnowledgeDocumentId(UUID.randomUUID()), 3).getFirst().approve();

        StructuredSpellDefinition edited = source.edit(source.level(), source.castingTime(), source.rangeArea(),
                source.components(), source.duration(), source.school(), "사용자가 확인한 내성", source.damageEffect());

        assertEquals(source.id(), edited.id());
        assertEquals(source.sourceDocumentId(), edited.sourceDocumentId());
        assertEquals(source.sourceLocator(), edited.sourceLocator());
        assertEquals(source.sourceVersion(), edited.sourceVersion());
        assertEquals(source.extractionVersion(), edited.extractionVersion());
        assertEquals(source.ownerPlanNumbers(), edited.ownerPlanNumbers());
        assertEquals("사용자가 확인한 내성", edited.attackSave());
        assertEquals(StructuredSpellDefinition.ReviewStatus.PENDING, edited.reviewStatus());
    }
}
