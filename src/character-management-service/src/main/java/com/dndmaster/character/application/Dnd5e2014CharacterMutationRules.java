package com.dndmaster.character.application;

import com.dndmaster.character.domain.CharacterMutationDecision;
import com.dndmaster.character.domain.CharacterMutationRules;
import com.dndmaster.character.domain.CharacterSheetData;
import com.dndmaster.character.domain.RuleViolation;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** D&D 5e 2014 character-state invariants enforced before aggregate mutation. */
public final class Dnd5e2014CharacterMutationRules implements CharacterMutationRules {
    private static final ObjectMapper JSON = new ObjectMapper();

    private static final Map<String, ArmorCategory> ARMOR_CATEGORIES = Map.ofEntries(
            Map.entry("패디드 아머", ArmorCategory.LIGHT),
            Map.entry("가죽 갑옷", ArmorCategory.LIGHT),
            Map.entry("스터디드 레더", ArmorCategory.LIGHT),
            Map.entry("하이드", ArmorCategory.MEDIUM),
            Map.entry("체인 셔츠", ArmorCategory.MEDIUM),
            Map.entry("스케일 메일", ArmorCategory.MEDIUM),
            Map.entry("브레스트플레이트", ArmorCategory.MEDIUM),
            Map.entry("하프 플레이트", ArmorCategory.MEDIUM),
            Map.entry("링 메일", ArmorCategory.HEAVY),
            Map.entry("체인 메일", ArmorCategory.HEAVY),
            Map.entry("스플린트", ArmorCategory.HEAVY),
            Map.entry("플레이트", ArmorCategory.HEAVY));

    private static final Set<String> METAL_ARMOR = Set.of(
            "체인 셔츠", "스케일 메일", "브레스트플레이트", "하프 플레이트",
            "링 메일", "체인 메일", "스플린트", "플레이트");

    private static final Set<String> HEAVY_ARMOR_CLERIC_DOMAINS = Set.of(
            "생명 권역", "자연 권역", "폭풍 권역", "전쟁 권역");

    @Override
    public CharacterMutationDecision evaluate(CharacterSheetData current, CharacterSheetData proposed) {
        List<RuleViolation> violations = new ArrayList<>();
        JsonNode build = parseObject(proposed.characterBuild(), "characterBuild", violations);
        JsonNode state = parseObject(proposed.characterState(), "characterState", violations);
        if (build == null || state == null) return CharacterMutationDecision.reject(violations);

        JsonNode equipped = state.path("equippedItems");
        if (!equipped.isObject()) {
            violations.add(violation("EQUIPPED_ITEMS_REQUIRED", "CHARACTER_STATE",
                    "장착 상태가 필요합니다.", Map.of("path", "characterState.equippedItems")));
            return CharacterMutationDecision.reject(violations);
        }

        Set<String> ownedEquipment = textSet(build.path("ownedEquipment"));
        Set<String> ownedWeaponIds = textSet(build.path("ownedWeaponIds"));
        String armor = text(equipped, "armor");
        boolean shield = equipped.path("shield").asBoolean(false);
        String mainHand = nullableText(equipped, "mainHandWeaponId");
        String offHand = nullableText(equipped, "offHandWeaponId");
        String twoHanded = nullableText(equipped, "twoHandedWeaponId");

        validateOwnership(armor, shield, mainHand, offHand, twoHanded, ownedEquipment, ownedWeaponIds, violations);
        validateHands(shield, mainHand, offHand, twoHanded, violations);
        validateArmorRules(proposed.characterClass(), text(build, "subclass"), armor, violations);

        return violations.isEmpty()
                ? CharacterMutationDecision.accept()
                : CharacterMutationDecision.reject(violations);
    }

    private static void validateOwnership(
            String armor,
            boolean shield,
            String mainHand,
            String offHand,
            String twoHanded,
            Set<String> ownedEquipment,
            Set<String> ownedWeaponIds,
            List<RuleViolation> violations) {
        if (!armor.isBlank() && !ownedEquipment.contains(armor)) {
            violations.add(violation("EQUIPPED_ARMOR_NOT_OWNED", "CHARACTER_RULE",
                    "보유하지 않은 갑옷은 장착할 수 없습니다.", Map.of("armor", armor)));
        }
        if (shield && ownedEquipment.stream().noneMatch(value -> value.contains("방패"))) {
            violations.add(violation("EQUIPPED_SHIELD_NOT_OWNED", "CHARACTER_RULE",
                    "보유하지 않은 방패는 장착할 수 없습니다.", Map.of()));
        }
        for (String weaponId : new String[] { mainHand, offHand, twoHanded }) {
            if (weaponId != null && !ownedWeaponIds.contains(weaponId)) {
                violations.add(violation("EQUIPPED_WEAPON_NOT_OWNED", "CHARACTER_RULE",
                        "보유하지 않은 무기는 장착할 수 없습니다.", Map.of("weaponId", weaponId)));
            }
        }
    }

    private static void validateHands(
            boolean shield,
            String mainHand,
            String offHand,
            String twoHanded,
            List<RuleViolation> violations) {
        if (twoHanded != null && (shield || mainHand != null || offHand != null)) {
            violations.add(violation("TWO_HANDED_EQUIPMENT_CONFLICT", "CHARACTER_RULE",
                    "양손 무기는 방패나 다른 손의 무기와 동시에 장착할 수 없습니다.", Map.of("weaponId", twoHanded)));
        }
        Set<String> weapons = new HashSet<>();
        for (String weapon : new String[] { mainHand, offHand, twoHanded }) {
            if (weapon != null && !weapons.add(weapon)) {
                violations.add(violation("DUPLICATE_EQUIPPED_WEAPON", "CHARACTER_RULE",
                        "같은 무기를 여러 장착 슬롯에 배치할 수 없습니다.", Map.of("weaponId", weapon)));
            }
        }
    }

    private static void validateArmorRules(
            String characterClass,
            String subclass,
            String armor,
            List<RuleViolation> violations) {
        if (armor.isBlank()) return;
        ArmorCategory category = ARMOR_CATEGORIES.get(armor);
        if (category != null && !armorProficiencies(characterClass, subclass).contains(category)) {
            violations.add(violation("ARMOR_NOT_PROFICIENT", "CHARACTER_RULE",
                    "숙련되지 않은 갑옷은 장착할 수 없습니다.",
                    Map.of("characterClass", characterClass, "armor", armor, "category", category.name())));
        }
        if ("드루이드".equals(characterClass) && METAL_ARMOR.contains(armor)) {
            violations.add(violation("DRUID_METAL_ARMOR_RESTRICTION", "CHARACTER_RULE",
                    "드루이드는 금속 갑옷을 장착할 수 없습니다.",
                    Map.of("characterClass", characterClass, "armor", armor, "material", "METAL")));
        }
    }

    private static Set<ArmorCategory> armorProficiencies(String characterClass, String subclass) {
        return switch (characterClass) {
            case "바바리안", "드루이드", "레인저" -> Set.of(ArmorCategory.LIGHT, ArmorCategory.MEDIUM);
            case "바드", "로그", "워락" -> Set.of(ArmorCategory.LIGHT);
            case "클레릭" -> HEAVY_ARMOR_CLERIC_DOMAINS.contains(subclass)
                    ? Set.of(ArmorCategory.LIGHT, ArmorCategory.MEDIUM, ArmorCategory.HEAVY)
                    : Set.of(ArmorCategory.LIGHT, ArmorCategory.MEDIUM);
            case "파이터", "팔라딘" -> Set.of(ArmorCategory.LIGHT, ArmorCategory.MEDIUM, ArmorCategory.HEAVY);
            default -> Set.of();
        };
    }

    private static JsonNode parseObject(String value, String field, List<RuleViolation> violations) {
        if (value == null || value.isBlank()) {
            violations.add(violation("CHARACTER_RULE_INPUT_REQUIRED", "CHARACTER_STATE",
                    "캐릭터 규칙 판정 입력이 필요합니다.", Map.of("field", field)));
            return null;
        }
        try {
            JsonNode node = JSON.readTree(value);
            if (!node.isObject()) throw new IllegalArgumentException("not an object");
            return node;
        } catch (Exception exception) {
            violations.add(violation("INVALID_CHARACTER_RULE_INPUT", "CHARACTER_STATE",
                    "캐릭터 규칙 판정 입력을 해석할 수 없습니다.", Map.of("field", field)));
            return null;
        }
    }

    private static Set<String> textSet(JsonNode node) {
        Set<String> values = new HashSet<>();
        if (node.isArray()) node.forEach(value -> {
            if (value.isTextual() && !value.asText().isBlank()) values.add(value.asText());
        });
        return values;
    }

    private static String text(JsonNode node, String field) {
        return node.path(field).asText("").trim();
    }

    private static String nullableText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || !value.isTextual() || value.asText().isBlank()) return null;
        return value.asText();
    }

    private static RuleViolation violation(
            String code, String category, String message, Map<String, String> parameters) {
        return new RuleViolation(code, category, "ERROR", message, parameters);
    }

    private enum ArmorCategory { LIGHT, MEDIUM, HEAVY }
}
