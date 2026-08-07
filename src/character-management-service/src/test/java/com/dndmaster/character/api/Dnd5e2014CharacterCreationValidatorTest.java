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
                 "equipmentSelections":{"weapon":"quarterstaff","focus":"component","pack":"scholar"},
                 "ruleChoices":{},"equippedItems":{"armor":"","shield":false,"mainHandWeaponId":"quarterstaff"},
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
    void acceptsExplicitFourD6DropLowestAbilityScores() {
        var request = request(
                "DND_5E_2014", "파이터", 1,
                "strength=12,dexterity=14,constitution=9,intelligence=8,wisdom=15,charisma=7",
                """
                {"schemaVersion":1,"subclass":"","skillProficiencies":["운동","위협"],"expertise":[],
                 "equipmentSelections":{"equipmentBundle":"fighter-start"},"ruleChoices":{"abilityScoreMethod":"ROLL_4D6_DROP_LOWEST"},
                 "equippedItems":{"armor":"쇠사슬 갑옷","shield":true}}
                """);
        assertDoesNotThrow(() -> Dnd5e2014CharacterCreationValidator.validateCreation(request));
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

    private static CharacterSheetController.CharacterSheetRequest request(
            String edition, String characterClass, int level, String abilities, String build) {
        return new CharacterSheetController.CharacterSheetRequest(
                null, UUID.randomUUID(), edition, "Aria", level, false,
                "인간", characterClass, "현자", abilities, "{}", build,
                "{\"equippedItems\":{}}", Map.of());
    }
}
