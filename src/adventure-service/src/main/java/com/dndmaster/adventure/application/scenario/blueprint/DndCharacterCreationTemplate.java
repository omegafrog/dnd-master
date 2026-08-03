package com.dndmaster.adventure.application.scenario.blueprint;

import com.dndmaster.adventure.domain.scenario.CharacterCreationBlueprint;
import com.dndmaster.adventure.domain.scenario.CharacterCreationBlueprintStatus;
import com.dndmaster.adventure.domain.scenario.InputMode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Edition-owned character creation skeleton.
 *
 * <p>The base D&D 5e structure is deterministic. Rulebook and storybook extraction may propose
 * extensions, restrictions, defaults, or scenario-only fields, but it never replaces the base
 * skeleton directly. Storybook proposals remain reviewable until the user explicitly resolves
 * them.</p>
 */
public final class DndCharacterCreationTemplate {
    private static final String STORY_PROPOSAL = "스토리북 제안: 적용 여부를 검토하세요";
    private static final String RULE_EXTENSION = "추가 룰북 속성 후보: 베이스 본에 추가할지 검토하세요";

    private DndCharacterCreationTemplate() {}

    public static CharacterCreationBlueprint apply(String edition, CharacterCreationBlueprint extracted) {
        List<FieldSpec> template = supportsDnd5e(edition) ? DND_5E_FIELDS : CORE_FIELDS;
        List<CharacterCreationBlueprint.Field> fields = new ArrayList<>();
        boolean reviewRequired = false;

        for (FieldSpec spec : template) {
            CharacterCreationBlueprint.Field base = templateField(spec);
            CharacterCreationBlueprint.Field discovered = extracted.fields().stream()
                    .filter(field -> field.key().equals(spec.key()))
                    .findFirst()
                    .orElse(null);
            if (discovered == null || isLegacyManualFallback(discovered)) {
                fields.add(base);
                continue;
            }
            CharacterCreationBlueprint.Field merged = mergeProposal(base, discovered);
            fields.add(merged);
            reviewRequired |= isProposal(merged);
        }

        for (CharacterCreationBlueprint.Field field : extracted.fields()) {
            if (template.stream().anyMatch(spec -> spec.key().equals(field.key()))
                    || field.key().startsWith("appearance.")
                    || isLegacyManualFallback(field)) continue;
            CharacterCreationBlueprint.Field proposal = proposalField(field);
            fields.add(proposal);
            reviewRequired |= isProposal(proposal);
        }

        List<String> diagnostics = new ArrayList<>(extracted.diagnostics());
        diagnostics.add(supportsDnd5e(edition)
                ? "base skeleton: D&D 5e character creation"
                : "base skeleton: core character identity");
        if (fields.stream().anyMatch(field -> field.diagnostics().contains(STORY_PROPOSAL))) {
            diagnostics.add("storybook overlays require explicit user review");
        }
        CharacterCreationBlueprintStatus status = reviewRequired
                ? CharacterCreationBlueprintStatus.NEEDS_REVIEW
                : CharacterCreationBlueprintStatus.READY;
        return new CharacterCreationBlueprint(extracted.revision(), status, fields, diagnostics);
    }

    private static CharacterCreationBlueprint.Field mergeProposal(
            CharacterCreationBlueprint.Field base,
            CharacterCreationBlueprint.Field discovered) {
        if ("STORYBOOK".equals(discovered.sourceType())) {
            List<String> suggestions = union(base.suggestions(), discovered.options(), discovered.suggestions());
            List<String> diagnostics = union(base.diagnostics(), discovered.diagnostics(), List.of(STORY_PROPOSAL));
            return new CharacterCreationBlueprint.Field(base.key(), base.options(), base.required(), "STORYBOOK",
                    discovered.evidence(), "CONFLICT_REVIEW", diagnostics, base.inputMode(), suggestions,
                    discovered.sourceQuote(), base.label(), base.value(), base.nodeId(), base.parentNodeId(),
                    discovered.confidence(), discovered.optionDetails());
        }
        if ("RULEBOOK".equals(discovered.sourceType()) && !discovered.options().isEmpty()) {
            List<String> options = base.inputMode() == InputMode.FREE_TEXT
                    ? base.options() : union(base.options(), discovered.options());
            List<String> diagnostics = discovered.options().stream().allMatch(base.options()::contains)
                    ? base.diagnostics()
                    : union(base.diagnostics(), discovered.diagnostics(), List.of(RULE_EXTENSION));
            String status = diagnostics.contains(RULE_EXTENSION) ? "CONFLICT_REVIEW" : base.inputStatus();
            return new CharacterCreationBlueprint.Field(base.key(), options, base.required(), "TEMPLATE",
                    discovered.evidence(), status, diagnostics, base.inputMode(), base.suggestions(),
                    discovered.sourceQuote(), base.label(), base.value(), base.nodeId(), base.parentNodeId(),
                    discovered.confidence(), List.of());
        }
        return base;
    }

    private static CharacterCreationBlueprint.Field proposalField(CharacterCreationBlueprint.Field field) {
        if ("STORYBOOK".equals(field.sourceType())) {
            return new CharacterCreationBlueprint.Field(field.key(), field.options(), field.required(), field.sourceType(),
                    field.evidence(), "CONFLICT_REVIEW", union(field.diagnostics(), List.of(STORY_PROPOSAL)),
                    field.inputMode(), field.suggestions(), field.sourceQuote(), field.label(), field.value(),
                    field.nodeId(), field.parentNodeId(), field.confidence(), field.optionDetails());
        }
        if ("RULEBOOK".equals(field.sourceType())) {
            return new CharacterCreationBlueprint.Field(field.key(), field.options(), field.required(), field.sourceType(),
                    field.evidence(), "CONFLICT_REVIEW", union(field.diagnostics(), List.of(RULE_EXTENSION)),
                    field.inputMode(), field.suggestions(), field.sourceQuote(), field.label(), field.value(),
                    field.nodeId(), field.parentNodeId(), field.confidence(), field.optionDetails());
        }
        return field;
    }

    private static boolean isProposal(CharacterCreationBlueprint.Field field) {
        return field.diagnostics().contains(STORY_PROPOSAL) || field.diagnostics().contains(RULE_EXTENSION)
                || "CONFLICT_REVIEW".equals(field.inputStatus());
    }

    private static boolean isLegacyManualFallback(CharacterCreationBlueprint.Field field) {
        return field.sourceType().equals("RULEBOOK") && field.options().isEmpty()
                && field.inputMode().equals(InputMode.FREE_TEXT);
    }

    private static CharacterCreationBlueprint.Field templateField(FieldSpec spec) {
        List<String> options = switch (spec.key()) {
            case "race" -> List.of("드워프", "엘프", "인간", "하플링");
            case "subrace" -> List.of("언덕 드워프", "산 드워프", "하이 엘프", "우드 엘프", "라이트풋 하플링", "스타우트 하플링");
            case "class" -> List.of("로그", "위저드", "클레릭", "파이터");
            case "background" -> List.of("수행사제", "사기꾼", "범죄자", "연예인", "민중 영웅", "길드 장인", "은둔자", "귀족", "이방인", "현자", "선원", "군인", "부랑아");
            case "class.skill_choices" -> List.of("곡예", "동물 조련", "비전학", "운동", "기만", "역사", "통찰", "위협", "수사", "의학", "자연", "지각", "공연", "설득", "종교", "손재주", "은신", "생존");
            case "ability_score_method" -> List.of("STANDARD_ARRAY", "ROLL_4D6_DROP_LOWEST", "POINT_BUY");
            case "starting_ability_scores.strength", "starting_ability_scores.dexterity",
                    "starting_ability_scores.constitution", "starting_ability_scores.intelligence",
                    "starting_ability_scores.wisdom", "starting_ability_scores.charisma" ->
                    List.of("15", "14", "13", "12", "10", "8");
            case "alignment" -> List.of("질서 선", "중립 선", "혼돈 선", "질서 중립", "중립", "혼돈 중립", "질서 악", "중립 악", "혼돈 악");
            case "equipment.acquisition_method" -> List.of("CLASS_AND_BACKGROUND", "STARTING_GOLD");
            default -> List.of();
        };
        String inputStatus = "TEMPLATE".equals(spec.origin()) ? "EXTRACTED" : "MANUAL_INPUT_REQUIRED";
        List<String> diagnostics = "MANUAL_INPUT_REQUIRED".equals(inputStatus)
                ? List.of("user input required") : List.of();
        List<CharacterCreationBlueprint.Field.OptionDetail> optionDetails = optionDetails(spec.key(), options);
        return new CharacterCreationBlueprint.Field(spec.key(), options, spec.required(), spec.origin(), List.of(),
                inputStatus, diagnostics, spec.inputMode(), List.of(), "", spec.label(), defaultValue(spec.key()), null, null,
                "HIGH", optionDetails);
    }

    private static String defaultValue(String key) {
        return switch (key) {
            case "level" -> "1";
            case "experience_points" -> "0";
            case "proficiency_bonus" -> "+2";
            case "passive_wisdom" -> "10";
            case "armor_class" -> "10";
            case "initiative" -> "0";
            case "speed" -> "30";
            case "hit_point_maximum" -> "0";
            default -> null;
        };
    }

    private static List<CharacterCreationBlueprint.Field.OptionDetail> optionDetails(String key, List<String> options) {
        if (options.isEmpty()) return List.of();
        return options.stream().map(value -> new CharacterCreationBlueprint.Field.OptionDetail(
                value, value, optionDescription(key, value), "", List.of())).toList();
    }

    private static String optionDescription(String key, String value) {
        if ("background".equals(key)) return switch (value) {
            case "수행사제" -> "신전과 종교 공동체에서 봉사하며 신앙을 배운 배경";
            case "사기꾼" -> "거짓 신분과 속임수로 살아온 배경";
            case "범죄자" -> "범죄 조직과 암시장에서 살아남은 배경";
            case "연예인" -> "공연과 이야기로 사람들의 관심을 끈 배경";
            case "민중 영웅" -> "평범한 사람들 사이에서 용기와 행동으로 이름을 알린 배경";
            case "길드 장인" -> "길드와 장인 사회에서 기술과 거래를 익힌 배경";
            case "은둔자" -> "외딴곳에서 고독과 성찰의 시간을 보낸 배경";
            case "귀족" -> "특권과 예법, 가문 정치에 익숙한 배경";
            case "이방인" -> "문명 밖 황야와 먼 지역에서 살아온 배경";
            case "현자" -> "학문과 연구로 지식을 축적한 배경";
            case "선원" -> "배와 항구에서 노동하고 항해한 배경";
            case "군인" -> "군대나 용병대에서 훈련과 전투를 경험한 배경";
            case "부랑아" -> "도시의 거리와 뒷골목에서 살아남은 배경";
            default -> "사용자 정의 배경";
        };
        if ("alignment".equals(key)) return switch (value) {
            case "질서 선" -> "질서와 선행을 중시합니다.";
            case "중립 선" -> "선행을 우선하지만 질서에는 유연합니다.";
            case "혼돈 선" -> "자유를 지키며 선행을 추구합니다.";
            case "질서 중립" -> "질서와 의무를 중시합니다.";
            case "중립" -> "상황에 따라 균형을 선택합니다.";
            case "혼돈 중립" -> "개인의 자유와 선택을 중시합니다.";
            case "질서 악" -> "질서와 통제를 악한 목적에 사용합니다.";
            case "중립 악" -> "이익과 생존을 위해 악행을 선택합니다.";
            case "혼돈 악" -> "충동과 파괴를 거리낌 없이 따릅니다.";
            default -> "성향 설명";
        };
        if ("class.skill_choices".equals(key)) return "클래스에서 선택 가능한 기술 숙련";
        return "";
    }

    @SafeVarargs
    private static <T> List<T> union(List<T>... values) {
        LinkedHashSet<T> merged = new LinkedHashSet<>();
        for (List<T> value : values) merged.addAll(value);
        return List.copyOf(merged);
    }

    private record FieldSpec(String key, String label, InputMode inputMode, boolean required, String origin) {
        private FieldSpec(String key, String label, InputMode inputMode, boolean required) {
            this(key, label, inputMode, required, "TEMPLATE");
        }
    }

    private static boolean supportsDnd5e(String edition) {
        return edition != null && edition.trim().toUpperCase(java.util.Locale.ROOT).startsWith("DND_5E");
    }

    private static final List<FieldSpec> CORE_FIELDS = List.of(
            new FieldSpec("name", "이름", InputMode.FREE_TEXT, true),
            new FieldSpec("level", "레벨", InputMode.FREE_TEXT, true));

    private static final List<FieldSpec> DND_5E_FIELDS = List.of(
            new FieldSpec("name", "이름", InputMode.FREE_TEXT, true),
            new FieldSpec("race", "종족", InputMode.SINGLE_SELECT, true),
            new FieldSpec("subrace", "하위 종족", InputMode.SINGLE_SELECT, false),
            new FieldSpec("race.option_selections", "종족 추가 선택", InputMode.MULTI_SELECT, false),
            new FieldSpec("class", "클래스", InputMode.SINGLE_SELECT, true),
            new FieldSpec("subclass", "하위 클래스", InputMode.SINGLE_SELECT, false),
            new FieldSpec("class.skill_choices", "클래스 기술 선택", InputMode.MULTI_SELECT, false),
            new FieldSpec("class.feature_choices", "클래스 특성 선택", InputMode.FIXED_VALUE, false),
            new FieldSpec("level", "레벨", InputMode.FIXED_VALUE, true),
            new FieldSpec("experience_points", "경험치", InputMode.FIXED_VALUE, false),
            new FieldSpec("background", "배경", InputMode.SINGLE_SELECT, true),
            new FieldSpec("alignment", "성향", InputMode.SINGLE_SELECT, false),
            new FieldSpec("ability_score_method", "능력치 생성 방식", InputMode.SINGLE_SELECT, true),
            new FieldSpec("starting_ability_scores.strength", "근력 (STR)", InputMode.SINGLE_SELECT, true),
            new FieldSpec("starting_ability_scores.dexterity", "민첩 (DEX)", InputMode.SINGLE_SELECT, true),
            new FieldSpec("starting_ability_scores.constitution", "건강 (CON)", InputMode.SINGLE_SELECT, true),
            new FieldSpec("starting_ability_scores.intelligence", "지능 (INT)", InputMode.SINGLE_SELECT, true),
            new FieldSpec("starting_ability_scores.wisdom", "지혜 (WIS)", InputMode.SINGLE_SELECT, true),
            new FieldSpec("starting_ability_scores.charisma", "매력 (CHA)", InputMode.SINGLE_SELECT, true),
            new FieldSpec("equipment.acquisition_method", "시작 장비 획득 방식", InputMode.SINGLE_SELECT, true),
            new FieldSpec("equipment.class_choices", "클래스 시작 장비", InputMode.FIXED_VALUE, false),
            new FieldSpec("equipment.background_items", "배경 시작 장비", InputMode.FIXED_VALUE, false),
            new FieldSpec("magic.cantrips", "소마법", InputMode.MULTI_SELECT, false),
            new FieldSpec("magic.spells", "주문", InputMode.MULTI_SELECT, false),
            new FieldSpec("personality_traits", "인격 특성", InputMode.SINGLE_SELECT, false),
            new FieldSpec("ideals", "이상", InputMode.SINGLE_SELECT, false),
            new FieldSpec("bonds", "유대", InputMode.SINGLE_SELECT, false),
            new FieldSpec("flaws", "단점", InputMode.SINGLE_SELECT, false),
            new FieldSpec("proficiency_bonus", "숙련 보너스", InputMode.FIXED_VALUE, false),
            new FieldSpec("saving_throws", "내성 굴림", InputMode.FIXED_VALUE, false),
            new FieldSpec("skills", "기술 숙련", InputMode.FIXED_VALUE, false),
            new FieldSpec("passive_wisdom", "수동 지혜(지각)", InputMode.FIXED_VALUE, false),
            new FieldSpec("armor_class", "방어도 (AC)", InputMode.FIXED_VALUE, false),
            new FieldSpec("initiative", "우선권", InputMode.FIXED_VALUE, false),
            new FieldSpec("speed", "이동속도", InputMode.FIXED_VALUE, false),
            new FieldSpec("hit_point_maximum", "최대 HP", InputMode.FIXED_VALUE, false),
            new FieldSpec("hit_dice", "히트 다이스", InputMode.FIXED_VALUE, false),
            new FieldSpec("attacks_spellcasting", "공격 및 주문시전", InputMode.FIXED_VALUE, false),
            new FieldSpec("equipment", "장비", InputMode.FIXED_VALUE, false),
            new FieldSpec("other_proficiencies_languages", "기타 숙련 및 언어", InputMode.FIXED_VALUE, false),
            new FieldSpec("features_traits", "특성 및 특징", InputMode.FIXED_VALUE, false));
}
