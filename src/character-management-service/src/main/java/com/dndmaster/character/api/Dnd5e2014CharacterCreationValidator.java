package com.dndmaster.character.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Server-side contract for newly created D&D 5e 2014 characters. */
final class Dnd5e2014CharacterCreationValidator {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final List<Integer> STANDARD_ARRAY = List.of(15, 14, 13, 12, 10, 8);
    private static final Set<String> ABILITIES = Set.of(
            "strength", "dexterity", "constitution", "intelligence", "wisdom", "charisma");
    private static final Map<String, Integer> CLASS_SKILL_MINIMUMS = Map.ofEntries(
            Map.entry("바바리안", 2), Map.entry("바드", 3), Map.entry("클레릭", 2),
            Map.entry("드루이드", 2), Map.entry("파이터", 2), Map.entry("몽크", 2),
            Map.entry("팔라딘", 2), Map.entry("레인저", 3), Map.entry("로그", 4),
            Map.entry("소서러", 2), Map.entry("워락", 2), Map.entry("위저드", 2));
    private static final Set<String> LEVEL_ONE_SUBCLASS_CLASSES = Set.of("클레릭", "소서러", "워락");
    private static final Map<String, Integer> CANTRIP_MINIMUMS = Map.of(
            "바드", 2, "클레릭", 3, "드루이드", 2, "소서러", 4, "워락", 2, "위저드", 3);
    private static final Map<String, Integer> FIRST_LEVEL_SPELL_MINIMUMS = Map.of(
            "바드", 4, "클레릭", 1, "드루이드", 1, "소서러", 2, "워락", 2, "위저드", 6);

    private Dnd5e2014CharacterCreationValidator() {}

    static void validateCreation(CharacterSheetController.CharacterSheetRequest request) {
        if (!"DND_5E_2014".equals(request.edition())) return;
        List<String> errors = new ArrayList<>();
        if (request.characterName() == null || request.characterName().isBlank()) errors.add("NAME_REQUIRED");
        if (request.level() != 1) errors.add("NEW_CHARACTER_LEVEL_MUST_BE_ONE");
        if (!CLASS_SKILL_MINIMUMS.containsKey(request.characterClass())) errors.add("UNSUPPORTED_CLASS");
        JsonNode build = parseRequiredObject(request.characterBuild(), "CHARACTER_BUILD_REQUIRED", errors);
        if (build != null) {
            validateAbilityScores(request.startingAbilities(), build, errors);
            validateBuild(request.characterClass(), build, errors);
        }
        JsonNode state = parseRequiredObject(request.characterState(), "CHARACTER_STATE_REQUIRED", errors);
        if (state != null && !state.has("equippedItems")) errors.add("EQUIPPED_ITEMS_REQUIRED");
        if (!errors.isEmpty()) throw badRequest(errors);
    }

    private static void validateAbilityScores(String startingAbilities, JsonNode build, List<String> errors) {
        if (startingAbilities == null || startingAbilities.isBlank()) {
            errors.add("STANDARD_ARRAY_REQUIRED");
            return;
        }
        Map<String, Integer> parsed = new HashMap<>();
        for (String item : startingAbilities.split(",")) {
            String[] parts = item.trim().split("=", 2);
            if (parts.length != 2 || !ABILITIES.contains(parts[0])) {
                errors.add("INVALID_ABILITY_ASSIGNMENT");
                return;
            }
            try {
                parsed.put(parts[0], Integer.parseInt(parts[1]));
            } catch (NumberFormatException exception) {
                errors.add("INVALID_ABILITY_ASSIGNMENT");
                return;
            }
        }
        List<Integer> values = new ArrayList<>(parsed.values());
        values.sort(java.util.Comparator.reverseOrder());
        String method = build.path("ruleChoices").path("abilityScoreMethod").asText("STANDARD_ARRAY");
        if ("ROLL_4D6_DROP_LOWEST".equals(method)) {
            if (parsed.size() != ABILITIES.size() || values.stream().anyMatch(value -> value < 3 || value > 18)) {
                errors.add("ROLL_4D6_SCORE_OUT_OF_RANGE");
            }
            return;
        }
        if (!"STANDARD_ARRAY".equals(method) || parsed.size() != ABILITIES.size() || !values.equals(STANDARD_ARRAY)) {
            errors.add("STANDARD_ARRAY_MISMATCH");
        }
    }

    private static JsonNode parseRequiredObject(String value, String missingCode, List<String> errors) {
        if (value == null || value.isBlank()) {
            errors.add(missingCode);
            return null;
        }
        try {
            JsonNode node = JSON.readTree(value);
            if (!node.isObject()) {
                errors.add("INVALID_JSON_OBJECT");
                return null;
            }
            return node;
        } catch (Exception exception) {
            errors.add("INVALID_JSON_OBJECT");
            return null;
        }
    }

    private static void validateBuild(String characterClass, JsonNode build, List<String> errors) {
        if (build.path("schemaVersion").asInt(0) < 1) errors.add("CHARACTER_BUILD_SCHEMA_REQUIRED");
        if (LEVEL_ONE_SUBCLASS_CLASSES.contains(characterClass) && build.path("subclass").asText("").isBlank()) {
            errors.add("SUBCLASS_REQUIRED_AT_LEVEL_ONE");
        }
        JsonNode skills = build.path("skillProficiencies");
        int skillMinimum = CLASS_SKILL_MINIMUMS.getOrDefault(characterClass, Integer.MAX_VALUE);
        if (!skills.isArray() || uniqueTextCount(skills) < skillMinimum) errors.add("INSUFFICIENT_SKILL_PROFICIENCIES");

        JsonNode expertise = build.path("expertise");
        int expertiseCount = expertise.isArray() ? uniqueTextCount(expertise) : 0;
        if ("로그".equals(characterClass)) {
            if (expertiseCount != 2) errors.add("ROGUE_EXPERTISE_COUNT_MISMATCH");
            if (skills.isArray() && expertise.isArray() && !textValues(skills).containsAll(textValues(expertise))) {
                errors.add("EXPERTISE_REQUIRES_PROFICIENCY");
            }
        } else if (expertiseCount > 0) {
            errors.add("EXPERTISE_NOT_AVAILABLE");
        }

        JsonNode equipmentSelections = build.path("equipmentSelections");
        if (!equipmentSelections.isObject() || equipmentSelections.isEmpty()) errors.add("EQUIPMENT_SELECTIONS_REQUIRED");
        if (!build.path("ruleChoices").isObject()) errors.add("RULE_CHOICES_REQUIRED");
        if (!build.path("equippedItems").isObject()) errors.add("EQUIPPED_ITEMS_REQUIRED");

        int cantripMinimum = CANTRIP_MINIMUMS.getOrDefault(characterClass, 0);
        if (cantripMinimum > 0 && uniqueTextCount(build.path("cantrips")) < cantripMinimum) {
            errors.add("CANTRIP_COUNT_MISMATCH");
        }
        int spellMinimum = FIRST_LEVEL_SPELL_MINIMUMS.getOrDefault(characterClass, 0);
        if (spellMinimum > 0 && uniqueTextCount(build.path("learnedOrPreparedSpells")) < spellMinimum) {
            errors.add("FIRST_LEVEL_SPELL_COUNT_MISMATCH");
        }
    }

    private static int uniqueTextCount(JsonNode node) {
        return node.isArray() ? textValues(node).size() : 0;
    }

    private static Set<String> textValues(JsonNode node) {
        Set<String> values = new HashSet<>();
        node.forEach(value -> {
            if (value.isTextual() && !value.asText().isBlank()) values.add(value.asText());
        });
        return values;
    }

    private static ResponseStatusException badRequest(List<String> errors) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, String.join(",", errors));
    }
}
