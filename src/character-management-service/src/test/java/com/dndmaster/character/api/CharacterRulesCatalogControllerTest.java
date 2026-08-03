package com.dndmaster.character.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CharacterRulesCatalogControllerTest {
    @Test
    void returnsTheCompleteDnd5e2014BaseCatalog() {
        CharacterSheetController controller = new CharacterSheetController(null);

        CharacterSheetController.CharacterRulesCatalogResponse catalog =
                controller.getCharacterRulesCatalog("DND_5E_2014");

        assertEquals("DND_5E_2014", catalog.baseSchema());
        assertEquals(1, catalog.revision());
        assertEquals(4, catalog.races().size());
        assertEquals(12, catalog.classes().size());
        assertEquals(13, catalog.backgrounds().size());
        assertTrue(catalog.classes().contains("드루이드"));
        assertTrue(catalog.backgrounds().contains("현자"));
    }
}
