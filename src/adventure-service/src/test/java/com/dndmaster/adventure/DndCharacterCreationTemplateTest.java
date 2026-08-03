package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.adventure.application.scenario.blueprint.CharacterCreationBlueprintCompiler;
import com.dndmaster.adventure.application.scenario.blueprint.DndCharacterCreationTemplate;
import com.dndmaster.adventure.domain.scenario.CharacterCreationBlueprint;
import com.dndmaster.adventure.domain.scenario.CharacterCreationBlueprintStatus;
import com.dndmaster.adventure.domain.scenario.InputMode;
import com.dndmaster.adventure.domain.scenario.ScenarioSourceReference;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DndCharacterCreationTemplateTest {
    private static final ScenarioSourceReference STORYBOOK = new ScenarioSourceReference(
            new com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId(UUID.randomUUID()), 1, "page:8");

    @Test
    void providesDeterministicDnd5eBaseSkeletonWhenSourcesAreSilent() {
        CharacterCreationBlueprint blueprint = DndCharacterCreationTemplate.apply("DND_5E_2014",
                new CharacterCreationBlueprintCompiler().compile(1, List.of()));

        assertEquals(CharacterCreationBlueprintStatus.READY, blueprint.status());
        assertEquals(InputMode.FREE_TEXT, blueprint.field("name").inputMode());
        for (String key : List.of("starting_ability_scores.strength", "starting_ability_scores.dexterity",
                "starting_ability_scores.constitution", "starting_ability_scores.intelligence",
                "starting_ability_scores.wisdom", "starting_ability_scores.charisma")) {
            assertTrue(blueprint.field(key).required(), key);
            assertEquals(InputMode.SINGLE_SELECT, blueprint.field(key).inputMode(), key);
            assertEquals("TEMPLATE", blueprint.field(key).sourceType(), key);
        }
        assertEquals(InputMode.FIXED_VALUE, blueprint.field("level").inputMode());
        assertEquals(List.of("드워프", "엘프", "인간", "하플링"), blueprint.field("race").options());
        assertEquals(List.of("로그", "위저드", "클레릭", "파이터"), blueprint.field("class").options());
        assertEquals(List.of("복사", "범죄자", "시골 영웅", "귀족", "학자", "군인", "맞춤 배경"),
                blueprint.field("background").options());
        assertEquals(List.of("CLASS_AND_BACKGROUND", "STARTING_GOLD"),
                blueprint.field("equipment.acquisition_method").options());
        for (String key : List.of("subrace", "subclass", "class.skill_choices", "magic.spells",
                "personality_traits", "ideals", "bonds", "flaws")) {
            assertFalse(blueprint.field(key).required(), key);
        }
        assertFalse(blueprint.fields().stream().anyMatch(field -> field.key().startsWith("appearance.")));
        assertEquals(InputMode.FIXED_VALUE, blueprint.field("armor_class").inputMode());
    }

    @Test
    void keepsBaseRaceOptionsAndTurnsStorybookRestrictionIntoReviewableOverlay() {
        CharacterCreationBlueprint extracted = new CharacterCreationBlueprintCompiler().compile(1, List.of(
                new CharacterCreationBlueprintCompiler.FieldCandidate("race", List.of("엘프", "인간"), true,
                        "STORYBOOK", STORYBOOK, "이 모험의 플레이어 캐릭터는 엘프나 인간이어야 한다.",
                        InputMode.SINGLE_SELECT, List.of())));

        CharacterCreationBlueprint blueprint = DndCharacterCreationTemplate.apply("DND_5E_2014", extracted);

        assertEquals(CharacterCreationBlueprintStatus.NEEDS_REVIEW, blueprint.status());
        assertEquals("STORYBOOK", blueprint.field("race").sourceType());
        assertEquals(List.of("드워프", "엘프", "인간", "하플링"), blueprint.field("race").options());
        assertEquals(List.of("엘프", "인간"), blueprint.field("race").suggestions());
        assertEquals("CONFLICT_REVIEW", blueprint.field("race").inputStatus());
        assertTrue(blueprint.field("race").diagnostics().stream().anyMatch(value -> value.contains("스토리북 제안")));
    }

    @Test
    void addsScenarioOnlyStorybookFieldAsOptionalProposal() {
        CharacterCreationBlueprint extracted = new CharacterCreationBlueprintCompiler().compile(1, List.of(
                new CharacterCreationBlueprintCompiler.FieldCandidate("scenario.personal_secret",
                        List.of("왕가의 후계자", "적 조직의 첩자"), true, "STORYBOOK", STORYBOOK,
                        "각 캐릭터는 비밀 하나를 선택할 수 있다.", InputMode.SINGLE_SELECT, List.of())));

        CharacterCreationBlueprint blueprint = DndCharacterCreationTemplate.apply("DND_5E_2014", extracted);

        assertEquals(CharacterCreationBlueprintStatus.NEEDS_REVIEW, blueprint.status());
        assertEquals(List.of("왕가의 후계자", "적 조직의 첩자"),
                blueprint.field("scenario.personal_secret").options());
        assertTrue(blueprint.field("scenario.personal_secret").diagnostics().stream()
                .anyMatch(value -> value.contains("스토리북 제안")));
    }

    @Test
    void replacesRulebookManualFallbackEvenWhenItHasSourceEvidence() {
        CharacterCreationBlueprint extracted = new CharacterCreationBlueprintCompiler().compile(1, List.of(
                new CharacterCreationBlueprintCompiler.FieldCandidate("race", List.of(), true, "RULEBOOK", STORYBOOK,
                        "Choose a race.", InputMode.FREE_TEXT, List.of())));

        CharacterCreationBlueprint blueprint = DndCharacterCreationTemplate.apply("DND_5E_2014", extracted);

        assertEquals(InputMode.SINGLE_SELECT, blueprint.field("race").inputMode());
        assertEquals(List.of("드워프", "엘프", "인간", "하플링"), blueprint.field("race").options());
    }
}
