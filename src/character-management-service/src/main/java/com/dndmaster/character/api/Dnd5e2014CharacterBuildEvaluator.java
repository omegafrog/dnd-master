package com.dndmaster.character.api;

import com.dndmaster.character.application.Dnd5e2014CharacterMutationRules;
import com.dndmaster.character.domain.CharacterMutationDecision;
import com.dndmaster.character.domain.CharacterSheetData2014;
import com.dndmaster.character.domain.RuleViolation;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Non-persisting, authoritative evaluation of an in-progress D&D 5e 2014 character build. */
final class Dnd5e2014CharacterBuildEvaluator {
    private static final Dnd5e2014CharacterMutationRules MUTATION_RULES = new Dnd5e2014CharacterMutationRules();
    private static final Map<String, Integer> HIT_DICE = Map.ofEntries(
            Map.entry("바바리안", 12), Map.entry("바드", 8), Map.entry("클레릭", 8),
            Map.entry("드루이드", 8), Map.entry("파이터", 10), Map.entry("몽크", 8),
            Map.entry("팔라딘", 10), Map.entry("레인저", 10), Map.entry("로그", 8),
            Map.entry("소서러", 6), Map.entry("워락", 8), Map.entry("위저드", 6));

    private Dnd5e2014CharacterBuildEvaluator() {}

    static Evaluation evaluate(CharacterSheetController.CharacterSheetRequest request) {
        String startingAbilities = startingAbilities(request);
        Map<String, Integer> scores = parseAbilities(startingAbilities);
        Map<String, Integer> modifiers = new LinkedHashMap<>();
        scores.forEach((ability, score) -> modifiers.put(ability, Math.floorDiv(score - 10, 2)));

        int level = Math.max(1, request.level());
        int proficiencyBonus = 2 + Math.max(0, level - 1) / 4;
        int constitutionModifier = modifiers.getOrDefault("constitution", 0);
        int dexterityModifier = modifiers.getOrDefault("dexterity", 0);
        int hitPointMaximum = Math.max(1, HIT_DICE.getOrDefault(request.characterClass(), 0) + constitutionModifier);

        CharacterSheetData2014 proposed = new CharacterSheetData2014(
                request.characterName(), level, request.inspiration(), request.race(), request.characterClass(),
                request.background(), startingAbilities, "", request.characterBuild(), request.characterState());
        CharacterMutationDecision mutationDecision = MUTATION_RULES.evaluate(proposed, proposed);

        Map<String, Object> derived = new LinkedHashMap<>();
        derived.put("abilityScores", scores);
        derived.put("abilityModifiers", modifiers);
        derived.put("proficiencyBonus", proficiencyBonus);
        derived.put("initiative", dexterityModifier);
        derived.put("hitPointMaximum", hitPointMaximum);

        return new Evaluation(mutationDecision.accepted(), derived, mutationDecision.violations());
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
            try {
                result.put(parts[0], Integer.parseInt(parts[1]));
            } catch (NumberFormatException ignored) {
                // Creation validation reports malformed ability input separately.
            }
        }
        return result;
    }

    record Evaluation(boolean valid, Map<String, Object> derived, List<RuleViolation> violations) {
        Evaluation {
            derived = Map.copyOf(derived);
            violations = List.copyOf(violations);
        }
    }
}
