package com.dndmaster.adventure.application.scenario.blueprint;

import com.dndmaster.adventure.domain.scenario.CharacterCreationBlueprint;
import com.dndmaster.adventure.domain.scenario.CharacterCreationBlueprintStatus;
import com.dndmaster.adventure.domain.scenario.InputMode;
import java.util.ArrayList;
import java.util.List;

/**
 * D&D 5e character-sheet baseline. Source-backed storybook and rulebook fields replace matching keys
 * with their selectable values; this template only keeps the sheet usable when those sources are silent.
 */
public final class DndCharacterCreationTemplate {
    private DndCharacterCreationTemplate() {}

    public static CharacterCreationBlueprint apply(String edition, CharacterCreationBlueprint extracted) {
        List<FieldSpec> template = supportsDnd5e(edition) ? DND_5E_FIELDS : CORE_FIELDS;
        List<CharacterCreationBlueprint.Field> fields = new ArrayList<>(extracted.fields().stream()
                .filter(field -> !isLegacyManualFallback(field))
                .toList());
        for (FieldSpec spec : template) {
            int index = java.util.stream.IntStream.range(0, fields.size())
                    .filter(i -> fields.get(i).key().equals(spec.key())).findFirst().orElse(-1);
            if (index >= 0 && !isLegacyManualFallback(fields.get(index))) continue;
            CharacterCreationBlueprint.Field templateField = templateField(spec);
            if (index >= 0) fields.set(index, templateField); else fields.add(templateField);
        }
        List<String> diagnostics = new ArrayList<>(extracted.diagnostics());
        diagnostics.add("base template: source-discovered game system");
        return new CharacterCreationBlueprint(extracted.revision(), CharacterCreationBlueprintStatus.NEEDS_REVIEW, fields, diagnostics);
    }

    private static boolean isLegacyManualFallback(CharacterCreationBlueprint.Field field) {
        return field.sourceType().equals("RULEBOOK") && field.options().isEmpty()
                && field.inputMode().equals(InputMode.FREE_TEXT);
    }

    private static CharacterCreationBlueprint.Field templateField(FieldSpec spec) {
        List<String> options = switch (spec.key()) {
            case "race" -> List.of("Dwarf", "Elf", "Halfling", "Human");
            case "class" -> List.of("Cleric", "Fighter", "Rogue", "Wizard");
            case "background" -> List.of("Acolyte", "Criminal", "Folk Hero", "Noble", "Sage", "Soldier");
            case "ability_score_method" -> List.of("STANDARD_ARRAY", "POINT_BUY", "ROLL_4D6_DROP_LOWEST");
            default -> List.of();
        };
        return new CharacterCreationBlueprint.Field(spec.key(), options, spec.required(), "TEMPLATE", List.of(),
                "MANUAL_INPUT_REQUIRED", List.of("user input required"), spec.inputMode(), List.of(), "", spec.label(),
                null, null, null, "LOW", List.of());
    }

    private record FieldSpec(String key, String label, InputMode inputMode, boolean required) {}

    private static boolean supportsDnd5e(String edition) {
        return edition != null && edition.trim().toUpperCase(java.util.Locale.ROOT).startsWith("DND_5E");
    }

    private static final List<FieldSpec> CORE_FIELDS = List.of(
            new FieldSpec("name", "이름", InputMode.FREE_TEXT, true),
            new FieldSpec("level", "레벨", InputMode.FREE_TEXT, true));

    private static final List<FieldSpec> DND_5E_FIELDS = List.of(
            // Character creation steps 1-4: identity, ancestry, class, ability scores, and background.
            new FieldSpec("name", "이름", InputMode.FREE_TEXT, true),
            new FieldSpec("race", "종족", InputMode.SINGLE_SELECT, true),
            new FieldSpec("class", "클래스", InputMode.SINGLE_SELECT, true),
            new FieldSpec("level", "레벨", InputMode.FREE_TEXT, true),
            new FieldSpec("experience_points", "경험치", InputMode.FREE_TEXT, false),
            new FieldSpec("background", "배경", InputMode.SINGLE_SELECT, true),
            new FieldSpec("alignment", "성향", InputMode.FREE_TEXT, false),
            new FieldSpec("ability_score_method", "능력치 생성 방식", InputMode.SINGLE_SELECT, true),
            new FieldSpec("starting_ability_scores.strength", "근력 (STR)", InputMode.FREE_TEXT, true),
            new FieldSpec("starting_ability_scores.dexterity", "민첩 (DEX)", InputMode.FREE_TEXT, true),
            new FieldSpec("starting_ability_scores.constitution", "건강 (CON)", InputMode.FREE_TEXT, true),
            new FieldSpec("starting_ability_scores.intelligence", "지능 (INT)", InputMode.FREE_TEXT, true),
            new FieldSpec("starting_ability_scores.wisdom", "지혜 (WIS)", InputMode.FREE_TEXT, true),
            new FieldSpec("starting_ability_scores.charisma", "매력 (CHA)", InputMode.FREE_TEXT, true),

            // Class, race, and equipment determine these values. Keep editable overrides for campaign rules.
            new FieldSpec("inspiration", "영감", InputMode.FREE_TEXT, false),
            new FieldSpec("proficiency_bonus", "숙련 보너스", InputMode.FREE_TEXT, false),
            new FieldSpec("saving_throws", "내성 굴림", InputMode.FREE_TEXT, false),
            new FieldSpec("skills", "기술 숙련", InputMode.FREE_TEXT, false),
            new FieldSpec("passive_wisdom", "수동 지혜(지각)", InputMode.FREE_TEXT, false),
            new FieldSpec("armor_class", "방어도 (AC)", InputMode.FREE_TEXT, false),
            new FieldSpec("initiative", "우선권", InputMode.FREE_TEXT, false),
            new FieldSpec("speed", "이동속도", InputMode.FREE_TEXT, false),
            new FieldSpec("hit_point_maximum", "최대 HP", InputMode.FREE_TEXT, false),
            new FieldSpec("current_hit_points", "현재 HP", InputMode.FREE_TEXT, false),
            new FieldSpec("temporary_hit_points", "임시 HP", InputMode.FREE_TEXT, false),
            new FieldSpec("hit_dice", "히트 다이스", InputMode.FREE_TEXT, false),
            new FieldSpec("death_saves", "죽음 내성 굴림", InputMode.FREE_TEXT, false),
            new FieldSpec("attacks_spellcasting", "공격 및 주문시전", InputMode.FREE_TEXT, false),
            new FieldSpec("equipment", "장비", InputMode.FREE_TEXT, false),
            new FieldSpec("other_proficiencies_languages", "기타 숙련 및 언어", InputMode.FREE_TEXT, false),
            new FieldSpec("features_traits", "특성 및 특징", InputMode.FREE_TEXT, false),

            // Character description and background role-play hooks.
            new FieldSpec("personality_traits", "인격 특성", InputMode.FREE_TEXT, false),
            new FieldSpec("ideals", "이상", InputMode.FREE_TEXT, false),
            new FieldSpec("bonds", "유대", InputMode.FREE_TEXT, false),
            new FieldSpec("flaws", "단점", InputMode.FREE_TEXT, false),
            new FieldSpec("appearance.age", "나이", InputMode.FREE_TEXT, false),
            new FieldSpec("appearance.height", "키", InputMode.FREE_TEXT, false),
            new FieldSpec("appearance.weight", "몸무게", InputMode.FREE_TEXT, false),
            new FieldSpec("appearance.eyes", "눈", InputMode.FREE_TEXT, false),
            new FieldSpec("appearance.skin", "피부", InputMode.FREE_TEXT, false),
            new FieldSpec("appearance.hair", "머리카락", InputMode.FREE_TEXT, false));
}
