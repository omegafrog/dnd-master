package com.dndmaster.character.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class Dnd5e2014CharacterBuildEvaluatorTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void derivesAuthoritativeWizardStatisticsIncludingRaceSkillsSavesAndMagic() throws Exception {
        var evaluation = Dnd5e2014CharacterBuildEvaluator.evaluate(request(
                "인간",
                "위저드",
                "strength=8,dexterity=14,constitution=13,intelligence=15,wisdom=12,charisma=10",
                """
                {"subrace":"","subclass":"","skillProficiencies":["비전학","지각"],"expertise":[],
                 "ownedEquipment":["쿼터스태프"],"ownedWeaponIds":["quarterstaff"]}
                """,
                """
                {"equippedItems":{"armor":"","shield":false,"mainHandWeaponId":"quarterstaff","offHandWeaponId":null,"twoHandedWeaponId":null}}
                """));

        assertTrue(evaluation.valid());
        assertEquals(2, evaluation.derived().get("proficiencyBonus"));
        assertEquals(2, evaluation.derived().get("initiative"));
        assertEquals(8, evaluation.derived().get("hitPointMaximum"));
        assertEquals(12, evaluation.derived().get("armorClass"));
        assertEquals(30, evaluation.derived().get("speed"));
        assertEquals(5, evaluation.derived().get("spellAttackBonus"));
        assertEquals(13, evaluation.derived().get("spellSaveDc"));
        assertEquals(14, evaluation.derived().get("passivePerception"));

        @SuppressWarnings("unchecked")
        Map<String, Integer> scores = (Map<String, Integer>) evaluation.derived().get("abilityScores");
        @SuppressWarnings("unchecked")
        Map<String, Integer> modifiers = (Map<String, Integer>) evaluation.derived().get("abilityModifiers");
        @SuppressWarnings("unchecked")
        Map<String, Integer> saves = (Map<String, Integer>) evaluation.derived().get("savingThrowBonuses");
        assertEquals(16, scores.get("intelligence"));
        assertEquals(3, modifiers.get("intelligence"));
        assertEquals(5, saves.get("intelligence"));
        assertEquals(3, saves.get("wisdom"));

        var serialized = JSON.readTree(evaluation.serializedDerived());
        assertEquals(12, serialized.path("armorClass").asInt());
        assertEquals(13, serialized.path("spellSaveDc").asInt());
    }

    @Test
    void appliesArmorDexterityCapShieldAndClassSavingThrows() {
        var evaluation = Dnd5e2014CharacterBuildEvaluator.evaluate(request(
                "인간",
                "파이터",
                "strength=15,dexterity=14,constitution=13,intelligence=8,wisdom=12,charisma=10",
                """
                {"subrace":"","subclass":"","skillProficiencies":["운동","지각"],"expertise":[],
                 "ownedEquipment":["스케일 메일","방패"],"ownedWeaponIds":[]}
                """,
                """
                {"equippedItems":{"armor":"스케일 메일","shield":true,"mainHandWeaponId":null,"offHandWeaponId":null,"twoHandedWeaponId":null}}
                """));

        assertTrue(evaluation.valid());
        assertEquals(18, evaluation.derived().get("armorClass"));
        @SuppressWarnings("unchecked")
        Map<String, Integer> saves = (Map<String, Integer>) evaluation.derived().get("savingThrowBonuses");
        assertEquals(5, saves.get("strength"));
        assertEquals(4, saves.get("constitution"));
    }

    @Test
    void expertiseOnlyAppliesToProficientSkills() {
        var evaluation = Dnd5e2014CharacterBuildEvaluator.evaluate(request(
                "하플링",
                "로그",
                "strength=8,dexterity=15,constitution=14,intelligence=13,wisdom=12,charisma=10",
                """
                {"subrace":"라이트풋 하플링","subclass":"","skillProficiencies":["곡예","은신","지각","수사"],
                 "expertise":["은신","종교"],"ownedEquipment":["가죽 갑옷"],"ownedWeaponIds":[]}
                """,
                """
                {"equippedItems":{"armor":"가죽 갑옷","shield":false,"mainHandWeaponId":null,"offHandWeaponId":null,"twoHandedWeaponId":null}}
                """));

        @SuppressWarnings("unchecked")
        Map<String, Dnd5e2014CharacterBuildEvaluator.SkillView> skills =
                (Map<String, Dnd5e2014CharacterBuildEvaluator.SkillView>) evaluation.derived().get("skillBonuses");
        assertTrue(skills.get("은신").expertise());
        assertEquals(7, skills.get("은신").bonus());
        assertFalse(skills.get("종교").expertise());
        assertFalse(skills.get("종교").proficient());
    }

    @Test
    void returnsStructuredMutationViolationsForDruidMetalArmor() {
        var evaluation = Dnd5e2014CharacterBuildEvaluator.evaluate(request(
                "인간",
                "드루이드",
                "strength=8,dexterity=14,constitution=13,intelligence=10,wisdom=15,charisma=12",
                """
                {"subrace":"","subclass":"","skillProficiencies":["자연","생존"],"expertise":[],
                 "ownedEquipment":["스케일 메일"],"ownedWeaponIds":[]}
                """,
                """
                {"equippedItems":{"armor":"스케일 메일","shield":false,"mainHandWeaponId":null,"offHandWeaponId":null,"twoHandedWeaponId":null}}
                """));

        assertFalse(evaluation.valid());
        assertTrue(evaluation.violations().stream()
                .anyMatch(violation -> violation.code().equals("DRUID_METAL_ARMOR_RESTRICTION")));
    }

    private static CharacterSheetController.CharacterSheetRequest request(
            String race, String characterClass, String abilities, String build, String state) {
        return new CharacterSheetController.CharacterSheetRequest(
                UUID.randomUUID(), UUID.randomUUID(), "DND_5E_2014", "Aria", 1, false,
                race, characterClass, "현자", abilities, "{\"armorClass\":999}", build, state, Map.of());
    }
}
