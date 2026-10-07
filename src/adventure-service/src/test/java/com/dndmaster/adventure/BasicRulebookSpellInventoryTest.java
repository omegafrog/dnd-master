package com.dndmaster.adventure;

import static org.assertj.core.api.Assertions.assertThat;

import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import com.dndmaster.adventure.domain.scenario.BasicRulebookSpellInventory;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BasicRulebookSpellInventoryTest {
    @Test
    void includes_every_official_spell_with_source_owner_and_no_false_support_claim() {
        var sourceDocumentId = new KnowledgeDocumentId(UUID.fromString("7b56c41d-f2d1-46b0-9f7a-87845bb5a879"));
        var spells = BasicRulebookSpellInventory.load(sourceDocumentId, 7);

        assertThat(spells).hasSize(126);
        assertThat(spells).extracting(spell -> spell.id()).doesNotHaveDuplicates();
        assertThat(spells).extracting(spell -> spell.sourceLocator()).doesNotHaveDuplicates();
        assertThat(spells).allSatisfy(spell -> {
            assertThat(spell.name()).isNotBlank();
            assertThat(spell.sourceDocumentId()).isEqualTo(sourceDocumentId.value());
            assertThat(spell.sourceLocator()).matches("page=\\d+;node=node-\\d+");
            assertThat(spell.sourceVersion()).isEqualTo(BasicRulebookSpellInventory.SOURCE_VERSION);
            assertThat(spell.extractionVersion()).isEqualTo(7);
            assertThat(spell.level()).isNotBlank();
            assertThat(spell.castingTime()).isNotBlank();
            assertThat(spell.rangeArea()).isNotBlank();
            assertThat(spell.components()).isNotBlank();
            assertThat(spell.duration()).isNotBlank();
            assertThat(spell.school()).isNotBlank();
            assertThat(spell.attackSave()).isNotBlank();
            assertThat(spell.damageEffect()).isNotBlank();
            assertThat(spell.ownerPlanNumbers()).allMatch(plan -> plan >= 368 && plan <= 372);
            assertThat(spell.executable()).isFalse();
            assertThat(spell.reviewStatus().name()).isEqualTo("PENDING");
        });
        assertThat(spells.subList(0, 60)).allSatisfy(spell -> assertThat(spell.ownerPlanNumbers()).isNotEmpty());
        assertThat(spells.subList(60, 126)).allSatisfy(spell -> {
            assertThat(spell.ownerPlanNumbers()).isEmpty();
            assertThat(spell.ownerEvidence()).startsWith("미검토:");
        });
    }
}
