package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import com.dndmaster.adventure.domain.scenario.BasicRulebookSpellInventory;
import com.dndmaster.adventure.domain.scenario.StructuredSpellDefinition;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SpellDefinitionReviewTest {
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
