package com.dndmaster.character.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class Dnd5e2014CharacterBuildEvaluatorTest {
    @Test
    void derivesCoreStatisticsWithoutPersisting() {
        var evaluation = Dnd5e2014CharacterBuildEvaluator.evaluate(request(
                "위저드",
                "strength=8,dexterity=14,constitution=13,intelligence=15,wisdom=12,charisma=10",
                """
                {"subclass":"","ownedEquipment":["쿼터스태프"],"ownedWeaponIds":["quarterstaff"]}
                """,
                """
                {"equippedItems":{"armor":"","shield":false,"mainHandWeaponId":"quarterstaff","offHandWeaponId":null,"twoHandedWeaponId":null}}
                """));

        assertTrue(evaluation.valid());
        assertEquals(2, evaluation.derived().get("proficiencyBonus"));
        assertEquals(2, evaluation.derived().get("initiative"));
        assertEquals(7, evaluation.derived().get("hitPointMaximum"));
        @SuppressWarnings("unchecked")
        Map<String, Integer> modifiers = (Map<String, Integer>) evaluation.derived().get("abilityModifiers");
        assertEquals(2, modifiers.get("intelligence"));
    }

    @Test
    void returnsStructuredMutationViolationsForDruidMetalArmor() {
        var evaluation = Dnd5e2014CharacterBuildEvaluator.evaluate(request(
                "드루이드",
                "strength=8,dexterity=14,constitution=13,intelligence=10,wisdom=15,charisma=12",
                """
                {"subclass":"","ownedEquipment":["스케일 메일"],"ownedWeaponIds":[]}
                """,
                """
                {"equippedItems":{"armor":"스케일 메일","shield":false,"mainHandWeaponId":null,"offHandWeaponId":null,"twoHandedWeaponId":null}}
                """));

        assertFalse(evaluation.valid());
        assertTrue(evaluation.violations().stream()
                .anyMatch(violation -> violation.code().equals("DRUID_METAL_ARMOR_RESTRICTION")));
    }

    private static CharacterSheetController.CharacterSheetRequest request(
            String characterClass, String abilities, String build, String state) {
        return new CharacterSheetController.CharacterSheetRequest(
                UUID.randomUUID(), UUID.randomUUID(), "DND_5E_2014", "Aria", 1, false,
                "인간", characterClass, "현자", abilities, "", build, state, Map.of());
    }
}
