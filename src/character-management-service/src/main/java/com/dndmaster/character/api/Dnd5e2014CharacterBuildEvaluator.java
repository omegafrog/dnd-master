package com.dndmaster.character.api;

import com.dndmaster.character.application.Dnd5e2014CharacterMutationRules;
import com.dndmaster.character.domain.CharacterMutationDecision;
import com.dndmaster.character.domain.CharacterSheetData2014;
import com.dndmaster.character.domain.RuleViolation;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Non-persisting, authoritative evaluation of an in-progress D&D 5e 2014 character build. */
final class Dnd5e2014CharacterBuildEvaluator {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Dnd5e2014CharacterMutationRules MUTATION_RULES = new Dnd5e2014CharacterMutationRules();
    private static final List<String> ABILITIES = List.of(
            "strength", "dexterity", "constitution", "intelligence", "wisdom", "charisma");
    private static final Map<String, Integer> HIT_DICE = Map.ofEntries(
            Map.entry("바바리안", 12), Map.entry("바드", 8), Map.entry("클레릭", 8),
            Map.entry("드루이드", 8), Map.entry("파이터", 10), Map.entry("몽크", 8),
            Map.entry("팔라딘", 10), Map.entry("레인저", 10), Map.entry("로그", 8),
            Map.entry("소서러", 6), Map.entry("워락", 8), Map.entry("위저드", 6));
    private static final Map<String, Set<String>> SAVING_THROWS = Map.ofEntries(
            Map.entry("바바리안", Set.of("strength", "constitution")), Map.entry("바드", Set.of("dexterity", "charisma")),
            Map.entry("클레릭", Set.of("wisdom", "charisma")), Map.entry("드루이드", Set.of("intelligence", "wisdom")),
            Map.entry("파이터", Set.of("strength", "constitution")), Map.entry("몽크", Set.of("strength", "dexterity")),
            Map.entry("팔라딘", Set.of("wisdom", "charisma")), Map.entry("레인저", Set.of("strength", "dexterity")),
            Map.entry("로그", Set.of("dexterity", "intelligence")), Map.entry("소서러", Set.of("constitution", "charisma")),
            Map.entry("워락", Set.of("wisdom", "charisma")), Map.entry("위저드", Set.of("intelligence", "wisdom")));
    private static final Map<String, String> SPELLCASTING_ABILITIES = Map.of(
            "바드", "charisma", "클레릭", "wisdom", "드루이드", "wisdom",
            "소서러", "charisma", "워락", "charisma", "위저드", "intelligence");
    private static final Map<String, String> SKILL_ABILITIES = Map.ofEntries(
            Map.entry("곡예", "dexterity"), Map.entry("동물 조련", "wisdom"), Map.entry("비전학", "intelligence"),
            Map.entry("운동", "strength"), Map.entry("기만", "charisma"), Map.entry("역사", "intelligence"),
            Map.entry("통찰", "wisdom"), Map.entry("위협", "charisma"), Map.entry("수사", "intelligence"),
            Map.entry("의학", "wisdom"), Map.entry("자연", "intelligence"), Map.entry("지각", "wisdom"),
            Map.entry("공연", "charisma"), Map.entry("설득", "charisma"), Map.entry("종교", "intelligence"),
            Map.entry("손재주", "dexterity"), Map.entry("은신", "dexterity"), Map.entry("생존", "wisdom"));
    private static final Map<String, Armor> ARMOR = Map.ofEntries(
            Map.entry("패디드 아머", new Armor(11, null)), Map.entry("가죽 갑옷", new Armor(11, null)),
            Map.entry("스터디드 레더", new Armor(12, null)), Map.entry("하이드", new Armor(12, 2)),
            Map.entry("체인 셔츠", new Armor(13, 2)), Map.entry("스케일 메일", new Armor(14, 2)),
            Map.entry("브레스트플레이트", new Armor(14, 2)), Map.entry("하프 플레이트", new Armor(15, 2)),
            Map.entry("링 메일", new Armor(14, 0)), Map.entry("체인 메일", new Armor(16, 0)),
            Map.entry("스플린트", new Armor(17, 0)), Map.entry("플레이트", new Armor(18, 0)));

    private Dnd5e2014CharacterBuildEvaluator() {}

    static Evaluation evaluate(CharacterSheetController.CharacterSheetRequest request) {
        String startingAbilities = startingAbilities(request);
        JsonNode build = parseObject(request.characterBuild());
        JsonNode state = parseObject(request.characterState());
        String subrace = build.path("subrace").asText("");
        String subclass = build.path("subclass").asText("");
        Map<String, Integer> scores = applyRaceBonuses(parseAbilities(startingAbilities), request.race(), subrace);
        Map<String, Integer> modifiers = modifiers(scores);
        int level = Math.max(1, Math.min(20, request.level()));
        int proficiencyBonus = 2 + (level - 1) / 4;

        CharacterSheetData2014 proposed = new CharacterSheetData2014(
                request.characterName(), level, request.inspiration(), request.race(), request.characterClass(),
                request.background(), startingAbilities, "", request.characterBuild(), request.characterState());
        CharacterMutationDecision mutationDecision = MUTATION_RULES.evaluate(proposed, proposed);

        JsonNode equipped = state.path("equippedItems");
        String armor = equipped.path("armor").asText("");
        boolean shield = equipped.path("shield").asBoolean(false);
        int hitDie = HIT_DICE.getOrDefault(request.characterClass(), 0);
        int hitPointMaximum = hitDie == 0 ? 0 : Math.max(1, hitDie + modifiers.getOrDefault("constitution", 0));
        Map<String, SkillView> skills = skills(build, modifiers, proficiencyBonus);
        int passivePerception = 10 + skills.getOrDefault(
                "지각", new SkillView(false, false, modifiers.getOrDefault("wisdom", 0))).bonus();
        String spellAbility = SPELLCASTING_ABILITIES.get(request.characterClass());
        Integer spellAttackBonus = spellAbility == null ? null : modifiers.getOrDefault(spellAbility, 0) + proficiencyBonus;

        Map<String, Object> derived = new LinkedHashMap<>();
        derived.put("abilityScores", scores);
        derived.put("abilityModifiers", modifiers);
        derived.put("proficiencyBonus", proficiencyBonus);
        derived.put("initiative", modifiers.getOrDefault("dexterity", 0));
        derived.put("speed", speed(request.race(), subrace));
        derived.put("hitDie", hitDie == 0 ? "" : "d" + hitDie);
        derived.put("hitPointMaximum", hitPointMaximum);
        derived.put("armorClass", armorClass(request.characterClass(), subclass, armor, shield, modifiers));
        derived.put("savingThrowBonuses", savingThrows(request.characterClass(), modifiers, proficiencyBonus));
        derived.put("skillBonuses", skills);
        derived.put("passivePerception", passivePerception);
        derived.put("attacks", Dnd5e2014AttackCalculator.calculate(
                request.characterClass(), build, state, modifiers, proficiencyBonus));
        derived.put("spellAttackBonus", spellAttackBonus);
        derived.put("spellSaveDc", spellAttackBonus == null ? null : 8 + spellAttackBonus);

        return new Evaluation(mutationDecision.accepted(), derived, mutationDecision.violations());
    }

    private static Map<String, Integer> applyRaceBonuses(Map<String, Integer> base, String race, String subrace) {
        Map<String, Integer> result = new LinkedHashMap<>();
        ABILITIES.forEach(ability -> result.put(ability, base.getOrDefault(ability, 0)));
        if ("인간".equals(race)) ABILITIES.forEach(ability -> result.compute(ability, (key, value) -> value + 1));
        if ("드워프".equals(race)) result.compute("constitution", (key, value) -> value + 2);
        if ("엘프".equals(race) || "하플링".equals(race)) result.compute("dexterity", (key, value) -> value + 2);
        switch (subrace) {
            case "언덕 드워프", "우드 엘프" -> result.compute("wisdom", (key, value) -> value + 1);
            case "산 드워프" -> result.compute("strength", (key, value) -> value + 2);
            case "하이 엘프" -> result.compute("intelligence", (key, value) -> value + 1);
            case "라이트풋 하플링" -> result.compute("charisma", (key, value) -> value + 1);
            case "스타우트 하플링" -> result.compute("constitution", (key, value) -> value + 1);
            default -> { }
        }
        return result;
    }

    private static Map<String, Integer> modifiers(Map<String, Integer> scores) {
        Map<String, Integer> result = new LinkedHashMap<>();
        ABILITIES.forEach(ability -> result.put(ability, Math.floorDiv(scores.getOrDefault(ability, 0) - 10, 2)));
        return result;
    }

    private static int armorClass(String characterClass, String subclass, String armorName, boolean shield, Map<String, Integer> modifiers) {
        int dexterity = modifiers.getOrDefault("dexterity", 0);
        Armor armor = ARMOR.get(armorName);
        if (armor != null) {
            int dexterityContribution = armor.dexterityCap() == null ? dexterity : Math.min(dexterity, armor.dexterityCap());
            return armor.base() + dexterityContribution + (shield ? 2 : 0);
        }
        int result = switch (characterClass) {
            case "바바리안" -> 10 + dexterity + modifiers.getOrDefault("constitution", 0);
            case "몽크" -> 10 + dexterity + (shield ? 0 : modifiers.getOrDefault("wisdom", 0));
            default -> 10 + dexterity;
        };
        if ("용의 혈통".equals(subclass)) result = Math.max(result, 13 + dexterity);
        if (shield && !"몽크".equals(characterClass)) result += 2;
        return result;
    }

    private static int speed(String race, String subrace) {
        if ("우드 엘프".equals(subrace)) return 35;
        if ("드워프".equals(race) || "하플링".equals(race)) return 25;
        if ("엘프".equals(race) || "인간".equals(race)) return 30;
        return 0;
    }

    private static Map<String, Integer> savingThrows(String characterClass, Map<String, Integer> modifiers, int proficiencyBonus) {
        Set<String> proficient = SAVING_THROWS.getOrDefault(characterClass, Set.of());
        Map<String, Integer> result = new LinkedHashMap<>();
        ABILITIES.forEach(ability -> result.put(ability,
                modifiers.getOrDefault(ability, 0) + (proficient.contains(ability) ? proficiencyBonus : 0)));
        return result;
    }

    private static Map<String, SkillView> skills(JsonNode build, Map<String, Integer> modifiers, int proficiencyBonus) {
        Set<String> proficient = textSet(build.path("skillProficiencies"));
        Set<String> expertise = textSet(build.path("expertise"));
        Map<String, SkillView> result = new LinkedHashMap<>();
        SKILL_ABILITIES.forEach((skill, ability) -> {
            boolean isProficient = proficient.contains(skill);
            boolean isExpert = isProficient && expertise.contains(skill);
            int multiplier = isExpert ? 2 : isProficient ? 1 : 0;
            result.put(skill, new SkillView(isProficient, isExpert,
                    modifiers.getOrDefault(ability, 0) + proficiencyBonus * multiplier));
        });
        return result;
    }

    private static String startingAbilities(CharacterSheetController.CharacterSheetRequest request) {
        if (request.startingAbilities() != null && !request.startingAbilities().isBlank()) return request.startingAbilities();
        if (request.blueprintValues() == null) return "";
        return request.blueprintValues().entrySet().stream()
                .filter(entry -> entry.getKey().startsWith("starting_ability_scores.")
                        && entry.getValue() != null && !entry.getValue().isBlank())
                .map(entry -> entry.getKey().substring("starting_ability_scores.".length()) + "=" + entry.getValue())
                .collect(java.util.stream.Collectors.joining(","));
    }

    private static Map<String, Integer> parseAbilities(String source) {
        Map<String, Integer> result = new LinkedHashMap<>();
        if (source == null || source.isBlank()) return result;
        for (String item : source.split(",")) {
            String[] parts = item.trim().split("=", 2);
            if (parts.length != 2) continue;
            try { result.put(parts[0], Integer.parseInt(parts[1])); }
            catch (NumberFormatException ignored) { }
        }
        return result;
    }

    private static JsonNode parseObject(String value) {
        if (value == null || value.isBlank()) return JSON.createObjectNode();
        try {
            JsonNode node = JSON.readTree(value);
            return node.isObject() ? node : JSON.createObjectNode();
        } catch (Exception ignored) {
            return JSON.createObjectNode();
        }
    }

    private static Set<String> textSet(JsonNode node) {
        Set<String> values = new java.util.LinkedHashSet<>();
        if (node.isArray()) node.forEach(value -> {
            if (value.isTextual() && !value.asText().isBlank()) values.add(value.asText());
        });
        return values;
    }

    record Evaluation(boolean valid, Map<String, Object> derived, List<RuleViolation> violations) {
        Evaluation {
            derived = Collections.unmodifiableMap(new LinkedHashMap<>(derived));
            violations = List.copyOf(violations);
        }

        String serializedDerived() {
            try { return JSON.writeValueAsString(derived); }
            catch (Exception exception) { throw new IllegalStateException("derived character statistics cannot be serialized", exception); }
        }
    }

    private record Armor(int base, Integer dexterityCap) {}
    record SkillView(boolean proficient, boolean expertise, int bonus) {}
}
