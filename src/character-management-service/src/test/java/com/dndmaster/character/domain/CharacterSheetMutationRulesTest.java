package com.dndmaster.character.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CharacterSheetMutationRulesTest {
    @Test
    void rejectedMutationDoesNotReplaceCharacterDataOrAdvanceVersion() {
        CharacterSheetData2014 original = data("드루이드", "{\"equippedItems\":{\"armor\":\"가죽 갑옷\"}}");
        CharacterSheetData2014 proposed = data("드루이드", "{\"equippedItems\":{\"armor\":\"플레이트 아머\"}}");
        CharacterSheet sheet = new CharacterSheet(
                CharacterSheetId.generate(), new SessionId(UUID.randomUUID()), SheetEdition.DND_5E_2014, original);
        CharacterSheetUpdate update = new CharacterSheetUpdate(
                SheetEdition.DND_5E_2014, proposed, InputMode.STRUCTURED_SHEET, UUID.randomUUID(), 0);
        CharacterMutationRules rules = (current, next) -> CharacterMutationDecision.reject(List.of(
                new RuleViolation(
                        "DRUID_METAL_ARMOR_RESTRICTION",
                        "CHARACTER_RULE",
                        "ERROR",
                        "드루이드는 금속 갑옷을 장착할 수 없습니다.",
                        Map.of("armor", "플레이트 아머"))));

        CharacterMutationRejectedException error = assertThrows(
                CharacterMutationRejectedException.class,
                () -> sheet.applyUpdate(update, rules));

        assertEquals("DRUID_METAL_ARMOR_RESTRICTION", error.violations().getFirst().code());
        assertEquals(original, sheet.data());
        assertEquals(0, sheet.version());
    }

    @Test
    void acceptedMutationReplacesCharacterData() {
        CharacterSheetData2014 original = data("파이터", "{}");
        CharacterSheetData2014 proposed = data("파이터", "{\"equippedItems\":{\"armor\":\"체인 메일\"}}");
        CharacterSheet sheet = new CharacterSheet(
                CharacterSheetId.generate(), new SessionId(UUID.randomUUID()), SheetEdition.DND_5E_2014, original);
        CharacterSheetUpdate update = new CharacterSheetUpdate(
                SheetEdition.DND_5E_2014, proposed, InputMode.STRUCTURED_SHEET, UUID.randomUUID(), 0);

        sheet.applyUpdate(update, CharacterMutationRules.allowAll());

        assertEquals(proposed, sheet.data());
    }

    private static CharacterSheetData2014 data(String characterClass, String characterState) {
        return new CharacterSheetData2014(
                "아리아", 1, false, "인간", characterClass, "학자",
                "strength=15,dexterity=14,constitution=13,intelligence=12,wisdom=10,charisma=8",
                "{}", "{}", characterState);
    }
}
