package com.dndmaster.character.application;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.character.domain.CharacterSheetData2014;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class Dnd5e2014CharacterMutationRulesTest {
    private final Dnd5e2014CharacterMutationRules rules = new Dnd5e2014CharacterMutationRules();

    @Test
    void rejectsMetalArmorForDruidEvenWhenOwnedAndMediumArmorProficient() {
        var proposed = data(
                "드루이드",
                "",
                "[\"스케일 메일\"]",
                "[]",
                "{\"armor\":\"스케일 메일\",\"shield\":false,\"mainHandWeaponId\":null,\"offHandWeaponId\":null,\"twoHandedWeaponId\":null}");

        var decision = rules.evaluate(proposed, proposed);

        assertFalse(decision.accepted());
        assertTrue(codes(decision).contains("DRUID_METAL_ARMOR_RESTRICTION"));
    }

    @Test
    void rejectsUnownedWeaponAndTwoHandedSlotConflict() {
        var proposed = data(
                "파이터",
                "",
                "[\"체인 메일\",\"방패\"]",
                "[\"longsword\"]",
                "{\"armor\":\"체인 메일\",\"shield\":true,\"mainHandWeaponId\":\"longsword\",\"offHandWeaponId\":null,\"twoHandedWeaponId\":\"greatsword\"}");

        var decision = rules.evaluate(proposed, proposed);

        assertFalse(decision.accepted());
        assertTrue(codes(decision).contains("EQUIPPED_WEAPON_NOT_OWNED"));
        assertTrue(codes(decision).contains("TWO_HANDED_EQUIPMENT_CONFLICT"));
    }

    @Test
    void acceptsOwnedProficientFighterEquipment() {
        var proposed = data(
                "파이터",
                "",
                "[\"체인 메일\",\"방패\"]",
                "[\"longsword\"]",
                "{\"armor\":\"체인 메일\",\"shield\":true,\"mainHandWeaponId\":\"longsword\",\"offHandWeaponId\":null,\"twoHandedWeaponId\":null}");

        assertTrue(rules.evaluate(proposed, proposed).accepted());
    }

    @Test
    void rejectsHeavyArmorForClericWithoutHeavyArmorDomain() {
        var proposed = data(
                "클레릭",
                "지식 권역",
                "[\"체인 메일\"]",
                "[]",
                "{\"armor\":\"체인 메일\",\"shield\":false,\"mainHandWeaponId\":null,\"offHandWeaponId\":null,\"twoHandedWeaponId\":null}");

        var decision = rules.evaluate(proposed, proposed);

        assertFalse(decision.accepted());
        assertTrue(codes(decision).contains("ARMOR_NOT_PROFICIENT"));
    }

    private static CharacterSheetData2014 data(
            String characterClass,
            String subclass,
            String ownedEquipment,
            String ownedWeaponIds,
            String equippedItems) {
        String build = "{\"subclass\":\"" + subclass + "\",\"ownedEquipment\":" + ownedEquipment
                + ",\"ownedWeaponIds\":" + ownedWeaponIds + "}";
        String state = "{\"equippedItems\":" + equippedItems + "}";
        return new CharacterSheetData2014(
                "테스트", 1, false, "인간", characterClass, "학자",
                "strength=15,dexterity=14,constitution=13,intelligence=12,wisdom=10,charisma=8",
                "{}", build, state);
    }

    private static Set<String> codes(com.dndmaster.character.domain.CharacterMutationDecision decision) {
        return decision.violations().stream().map(violation -> violation.code()).collect(Collectors.toSet());
    }
}
