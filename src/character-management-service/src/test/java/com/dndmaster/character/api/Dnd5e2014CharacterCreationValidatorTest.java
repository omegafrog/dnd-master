package com.dndmaster.character.api;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class Dnd5e2014CharacterCreationValidatorTest {
    @Test
    void acceptsCompleteLevelOneWizardBuild() {
        var request = request(
                "DND_5E_2014", "위저드", 1,
                "strength=8,dexterity=14,constitution=13,intelligence=15,wisdom=12,charisma=10",
                """
                {"schemaVersion":1,"subclass":"","skillProficiencies":["비전학","역사"],"expertise":[],
                 "equipmentSelections":{"armor":"","weaponAndShield":"","rangedWeapon":"","pack":"학자의 꾸러미"},
                 "ruleChoices":{},"equippedItems":{"armor":"","shield":false,"mainHandWeaponId":"quarterstaff"},
                 "ownedEquipment":["지팡이","구성 요소 주머니","학자의 꾸러미"],"ownedWeaponIds":["quarterstaff"],
                 "cantrips":["마법사의 손","빛","불화살"],
                 "learnedOrPreparedSpells":["마법 갑주","마법 화살","방패","수면","천둥파도","탐지 마법"]}
                """);
        assertDoesNotThrow(() -> Dnd5e2014CharacterCreationValidator.validateCreation(request));
    }

    @Test
    void rejectsWrongStandardArrayAndMissingSubclass() {
        var request = request(
                "DND_5E_2014", "클레릭", 1,
                "strength=15,dexterity=15,constitution=13,intelligence=12,wisdom=10,charisma=8",
                """
                {"schemaVersion":1,"subclass":"","skillProficiencies":["통찰","종교"],"expertise":[],
                 "equipmentSelections":{"armor":"scale"},"ruleChoices":{},
                 "equippedItems":{"armor":"스케일 메일","shield":true},
                 "cantrips":["가이던스","빛","신성한 불꽃"],"learnedOrPreparedSpells":["축복"]}
                """);
        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> Dnd5e2014CharacterCreationValidator.validateCreation(request));
        assertTrue(error.getReason().contains("STANDARD_ARRAY_MISMATCH"));
        assertTrue(error.getReason().contains("SUBCLASS_REQUIRED_AT_LEVEL_ONE"));
    }

    @Test
    void rejectsInvalidRogueExpertise() {
        var request = request(
                "DND_5E_2014", "로그", 1,
                "strength=8,dexterity=15,constitution=14,intelligence=13,wisdom=12,charisma=10",
                """
                {"schemaVersion":1,"subclass":"","skillProficiencies":["곡예","은신","지각","수사"],
                 "expertise":["곡예","종교"],"equipmentSelections":{"weapon":"rapier"},"ruleChoices":{},
                 "equippedItems":{"armor":"가죽 갑옷","shield":false,"mainHandWeaponId":"rapier"},
                 "cantrips":[],"learnedOrPreparedSpells":[]}
                """);
        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> Dnd5e2014CharacterCreationValidator.validateCreation(request));
        assertTrue(error.getReason().contains("EXPERTISE_REQUIRES_PROFICIENCY"));
    }

    @Test
    void leavesExisting2024CreationContractUntouched() {
        var request = request("DND_5E_2024", null, 3, null, null);
        assertDoesNotThrow(() -> Dnd5e2014CharacterCreationValidator.validateCreation(request));
    }

    @Test
    void acceptsCanonicalBasicRulesFighterWithItsStartingEquipment() {
        assertDoesNotThrow(() -> Dnd5e2014CharacterCreationValidator.validateCreation(request(
                "인간", "파이터", "군인", fighterBuild())));
    }

    @Test
    void acceptsTheOtherBasicRulesClassStartingEquipmentProfiles() {
        assertDoesNotThrow(() -> Dnd5e2014CharacterCreationValidator.validateCreation(request(
                "하플링", "로그", "범죄자", """
                {"schemaVersion":1,"skillProficiencies":["곡예","은신","지각","수사"],"expertise":["은신","수사"],
                "equipmentSelections":{"armor":"가죽 갑옷","weaponAndShield":"","rangedWeapon":""},"ruleChoices":{},
                "equippedItems":{"armor":"가죽 갑옷","shield":false,"mainHandWeaponId":"shortsword"},
                "ownedEquipment":["가죽 갑옷","단검","숏소드","도둑 도구","도둑의 꾸러미"],"ownedWeaponIds":["dagger","shortsword"]}
                """)));
        assertDoesNotThrow(() -> Dnd5e2014CharacterCreationValidator.validateCreation(request(
                "드워프", "클레릭", "수행사제", """
                {"schemaVersion":1,"subclass":"생명 권역","skillProficiencies":["통찰","종교"],"expertise":[],
                "equipmentSelections":{"armor":"스케일 메일","weaponAndShield":"메이스와 방패","rangedWeapon":""},"ruleChoices":{},
                "equippedItems":{"armor":"스케일 메일","shield":true,"mainHandWeaponId":"mace"},
                "ownedEquipment":["스케일 메일","방패","메이스","성표","사제의 꾸러미"],"ownedWeaponIds":["mace"],
                "cantrips":["가이던스","빛","신성한 불꽃"],"learnedOrPreparedSpells":["축복"]}
                """)));
    }

    @Test
    void rejectsNonCanonicalExtractedLabelsAndIncompleteFighterEquipment() {
        assertThrows(ResponseStatusException.class, () -> Dnd5e2014CharacterCreationValidator.validateCreation(request(
                "Human", "Fighter", "grizzled soldier", fighterBuild())));
        assertThrows(ResponseStatusException.class, () -> Dnd5e2014CharacterCreationValidator.validateCreation(request(
                "인간", "파이터", "군인", fighterBuild().replace(",\"light-crossbow\"", ""))));
    }

    private static CharacterSheetController.CharacterSheetRequest request(
            String race, String characterClass, String background, String build) {
        return new CharacterSheetController.CharacterSheetRequest(UUID.randomUUID(), UUID.randomUUID(), "DND_5E_2014",
                "마린 발렌", 1, false, race, characterClass, background,
                "strength=15,dexterity=14,constitution=13,intelligence=12,wisdom=10,charisma=8", null, build,
                "{\"equippedItems\":{\"armor\":\"체인 메일\",\"shield\":true,\"mainHandWeaponId\":\"longsword\"}}", Map.of());
    }

    private static CharacterSheetController.CharacterSheetRequest request(
            String edition, String characterClass, int level, String abilities, String build) {
        return new CharacterSheetController.CharacterSheetRequest(
                null, UUID.randomUUID(), edition, "Aria", level, false,
                "인간", characterClass, "현자", abilities, "{}", build,
                "{\"equippedItems\":{}}", Map.of());
    }

    private static String fighterBuild() {
        return """
                {"schemaVersion":1,"skillProficiencies":["운동","지각"],"expertise":[],
                "equipmentSelections":{"armor":"체인 메일","weaponAndShield":"롱소드와 방패","rangedWeapon":"라이트 크로스보우와 볼트 20개","pack":"던전 탐험가 팩"},
                "ruleChoices":{},"equippedItems":{"armor":"체인 메일","shield":true,"mainHandWeaponId":"longsword"},
                "ownedEquipment":["체인 메일","방패","라이트 크로스보우","볼트 20개","던전 탐험가 팩"],"ownedWeaponIds":["longsword","light-crossbow"]}
                """;
    }
}
