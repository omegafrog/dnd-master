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
        assertTrue(blueprint.field("subclass").options().containsAll(List.of("생명 권역", "용의 혈통", "대마족")));
        assertEquals(List.of("수행사제", "사기꾼", "범죄자", "연예인", "민중 영웅", "길드 장인", "은둔자", "귀족", "이방인", "현자", "선원", "군인", "부랑아"),
                blueprint.field("background").options());
        assertEquals(List.of("CLASS_AND_BACKGROUND", "STARTING_GOLD"),
                blueprint.field("equipment.acquisition_method").options());
        assertTrue(blueprint.field("magic.cantrips").options().containsAll(List.of("가이던스", "마법사의 손", "진실의 일격")));
        assertTrue(blueprint.field("magic.spells").options().containsAll(List.of("축복", "마법 갑주", "마법 화살")));
        assertTrue(blueprint.field("magic.cantrips").options().size() > 10);
        assertTrue(blueprint.field("magic.spells").options().size() > 10);
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
    void preserves_storybook_candidate_label_when_overlaying_a_template_field() {
        CharacterCreationBlueprint extracted = new CharacterCreationBlueprint(1, CharacterCreationBlueprintStatus.NEEDS_REVIEW,
                List.of(new CharacterCreationBlueprint.Field("race", List.of("엘프"), true, "STORYBOOK", List.of(STORYBOOK),
                        "CONFLICT_REVIEW", List.of(), InputMode.SINGLE_SELECT, List.of(), "Only elves.",
                        "Campaign race", null, "race-node", null, "HIGH")), List.of());

        CharacterCreationBlueprint blueprint = DndCharacterCreationTemplate.apply("DND_5E_2014", extracted);

        assertEquals("Campaign race", blueprint.field("race").label());
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

    @Test
    void normalizesKnown2014RulebookValuesWithoutMakingRawExtractionSelectable() {
        CharacterCreationBlueprint extracted = new CharacterCreationBlueprintCompiler().compile(1, List.of(
                new CharacterCreationBlueprintCompiler.FieldCandidate("class", List.of("Fighter"), true,
                        "RULEBOOK", STORYBOOK, "Fighter", InputMode.SINGLE_SELECT, List.of()),
                new CharacterCreationBlueprintCompiler.FieldCandidate("background", List.of("Soldier", "grizzled soldier"), true,
                        "RULEBOOK", STORYBOOK, "Soldier", InputMode.SINGLE_SELECT, List.of())));

        CharacterCreationBlueprint blueprint = DndCharacterCreationTemplate.apply("DND_5E_2014", extracted);

        assertEquals(List.of("로그", "위저드", "클레릭", "파이터"), blueprint.field("class").options());
        assertEquals(List.of("수행사제", "사기꾼", "범죄자", "연예인", "민중 영웅", "길드 장인", "은둔자", "귀족", "이방인", "현자", "선원", "군인", "부랑아"),
                blueprint.field("background").options());
        assertFalse(blueprint.field("class").options().contains("Fighter"));
        assertFalse(blueprint.field("background").options().contains("Soldier"));
        assertFalse(blueprint.field("background").options().contains("grizzled soldier"));
        assertEquals(List.of("grizzled soldier"), blueprint.field("background").suggestions());
        assertEquals("CONFLICT_REVIEW", blueprint.field("background").inputStatus());
    }

    @Test
    void doesNotTreat2024AsThe2014BaseTemplate() {
        CharacterCreationBlueprint blueprint = DndCharacterCreationTemplate.apply("DND_5E_2024",
                new CharacterCreationBlueprintCompiler().compile(1, List.of()));

        assertTrue(blueprint.fields().stream().anyMatch(field -> field.key().equals("name")));
        assertFalse(blueprint.fields().stream().anyMatch(field -> field.key().equals("race")));
        assertFalse(blueprint.fields().stream().anyMatch(field -> field.key().equals("background")));
    }
}
