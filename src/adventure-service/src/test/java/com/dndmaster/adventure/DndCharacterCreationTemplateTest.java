package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.adventure.application.scenario.blueprint.CharacterCreationBlueprintCompiler;
import com.dndmaster.adventure.application.scenario.blueprint.DndCharacterCreationTemplate;
import com.dndmaster.adventure.domain.scenario.CharacterCreationBlueprint;
import com.dndmaster.adventure.domain.scenario.InputMode;
import com.dndmaster.adventure.domain.scenario.ScenarioSourceReference;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DndCharacterCreationTemplateTest {
    private static final ScenarioSourceReference STORYBOOK = new ScenarioSourceReference(
            new com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId(UUID.randomUUID()), 1, "page:8");

    @Test
    void providesDnd5eSheetFieldsWhenSourcesAreSilent() {
        CharacterCreationBlueprint blueprint = DndCharacterCreationTemplate.apply("DND_5E_2014",
                new CharacterCreationBlueprintCompiler().compile(1, List.of()));

        for (String key : List.of("name", "level",
                "starting_ability_scores.strength", "starting_ability_scores.dexterity",
                "starting_ability_scores.constitution", "starting_ability_scores.intelligence",
                "starting_ability_scores.wisdom", "starting_ability_scores.charisma")) {
            assertTrue(blueprint.field(key).required(), key);
            assertEquals(InputMode.FREE_TEXT, blueprint.field(key).inputMode(), key);
            assertEquals("TEMPLATE", blueprint.field(key).sourceType(), key);
        }
        assertEquals(InputMode.SINGLE_SELECT, blueprint.field("race").inputMode());
        assertEquals(List.of("Dwarf", "Elf", "Halfling", "Human"), blueprint.field("race").options());
        assertEquals(InputMode.SINGLE_SELECT, blueprint.field("class").inputMode());
        assertEquals(List.of("Cleric", "Fighter", "Rogue", "Wizard"), blueprint.field("class").options());
        assertEquals(InputMode.SINGLE_SELECT, blueprint.field("background").inputMode());
        assertEquals(List.of("Acolyte", "Criminal", "Folk Hero", "Noble", "Sage", "Soldier"), blueprint.field("background").options());
        for (String key : List.of("armor_class", "hit_point_maximum", "equipment", "features_traits",
                "personality_traits", "ideals", "bonds", "flaws", "appearance.age")) {
            assertFalse(blueprint.field(key).required(), key);
        }
    }

    @Test
    void retainsStorybookChoicesOverTemplateFreeText() {
        CharacterCreationBlueprint extracted = new CharacterCreationBlueprintCompiler().compile(1, List.of(
                new CharacterCreationBlueprintCompiler.FieldCandidate("race", List.of("Eladrin", "Shifter"), true,
                        "STORYBOOK", STORYBOOK, "The campaign permits only Eladrin and Shifter heroes.",
                        InputMode.SINGLE_SELECT, List.of())));

        CharacterCreationBlueprint blueprint = DndCharacterCreationTemplate.apply("DND_5E_2014", extracted);

        assertEquals("STORYBOOK", blueprint.field("race").sourceType());
        assertEquals(InputMode.SINGLE_SELECT, blueprint.field("race").inputMode());
        assertEquals(List.of("Eladrin", "Shifter"), blueprint.field("race").options());
    }

    @Test
    void replacesRulebookManualFallbackEvenWhenItHasSourceEvidence() {
        CharacterCreationBlueprint extracted = new CharacterCreationBlueprintCompiler().compile(1, List.of(
                new CharacterCreationBlueprintCompiler.FieldCandidate("race", List.of(), true, "RULEBOOK", STORYBOOK,
                        "Choose a race.", InputMode.FREE_TEXT, List.of())));

        CharacterCreationBlueprint blueprint = DndCharacterCreationTemplate.apply("DND_5E_2014", extracted);

        assertEquals(InputMode.SINGLE_SELECT, blueprint.field("race").inputMode());
        assertEquals(List.of("Dwarf", "Elf", "Halfling", "Human"), blueprint.field("race").options());
    }
}
