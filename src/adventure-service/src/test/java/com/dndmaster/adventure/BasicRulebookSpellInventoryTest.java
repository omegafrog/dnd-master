package com.dndmaster.adventure;

import static org.assertj.core.api.Assertions.assertThat;

import com.dndmaster.adventure.domain.scenario.BasicRulebookSpellInventory;
import org.junit.jupiter.api.Test;

class BasicRulebookSpellInventoryTest {
    @Test
    void includes_every_official_spell_with_source_owner_and_no_false_support_claim() {
        var spells = BasicRulebookSpellInventory.load();

        assertThat(spells).hasSize(304);
        assertThat(spells).extracting(spell -> spell.id()).doesNotHaveDuplicates();
        assertThat(spells).allSatisfy(spell -> {
            assertThat(spell.name()).isNotBlank();
            assertThat(spell.sourceUrl()).startsWith("https://www.dndbeyond.com/spells/");
            assertThat(spell.sourceVersion()).isEqualTo(BasicRulebookSpellInventory.SOURCE_VERSION);
            assertThat(spell.extractionVersion()).isEqualTo(BasicRulebookSpellInventory.EXTRACTION_VERSION);
            assertThat(spell.level()).isNotBlank();
            assertThat(spell.castingTime()).isNotBlank();
            assertThat(spell.rangeArea()).isNotBlank();
            assertThat(spell.components()).isNotBlank();
            assertThat(spell.duration()).isNotBlank();
            assertThat(spell.school()).isNotBlank();
            assertThat(spell.attackSave()).isNotBlank();
            assertThat(spell.damageEffect()).isNotBlank();
            assertThat(spell.ownerPlanNumbers()).contains(368);
            assertThat(spell.ownerPlanNumbers()).allMatch(plan -> plan >= 368 && plan <= 372);
            assertThat(spell.executable()).isFalse();
            assertThat(spell.reviewStatus().name()).isEqualTo("PENDING");
        });
    }
}
