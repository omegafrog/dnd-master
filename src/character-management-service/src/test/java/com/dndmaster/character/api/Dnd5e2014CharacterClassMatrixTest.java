package com.dndmaster.character.api;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class Dnd5e2014CharacterClassMatrixTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String STANDARD_ARRAY =
            "strength=15,dexterity=14,constitution=13,intelligence=12,wisdom=10,charisma=8";

    @ParameterizedTest(name = "{0} 1레벨 생성 요청을 허용한다")
    @MethodSource("classBuilds")
    void acceptsEverySupportedLevelOneClass(String characterClass, BuildCase buildCase) {
        var request = new CharacterSheetController.CharacterSheetRequest(
                null,
                UUID.randomUUID(),
                "DND_5E_2014",
                characterClass + " 테스트",
                1,
                false,
                "인간",
                characterClass,
                "학자",
                STANDARD_ARRAY,
                "{}",
                buildJson(buildCase),
                "{\"equippedItems\":{}}",
                Map.of());

        assertDoesNotThrow(() -> Dnd5e2014CharacterCreationValidator.validateCreation(request));
    }

    private static Stream<Arguments> classBuilds() {
        return Stream.of(
                Arguments.of("바바리안", build(2, 0, 0, "", false)),
                Arguments.of("바드", build(3, 2, 4, "", false)),
                Arguments.of("클레릭", build(2, 3, 1, "생명 권역", false)),
                Arguments.of("드루이드", build(2, 2, 1, "", false)),
                Arguments.of("파이터", build(2, 0, 0, "", false)),
                Arguments.of("몽크", build(2, 0, 0, "", false)),
                Arguments.of("팔라딘", build(2, 0, 0, "", false)),
                Arguments.of("레인저", build(3, 0, 0, "", false)),
                Arguments.of("로그", build(4, 0, 0, "", true)),
                Arguments.of("소서러", build(2, 4, 2, "용의 혈통", false)),
                Arguments.of("워락", build(2, 2, 2, "대악마", false)),
                Arguments.of("위저드", build(2, 3, 6, "", false)));
    }

    private static BuildCase build(
            int skillCount,
            int cantripCount,
            int spellCount,
            String subclass,
            boolean rogueExpertise) {
        List<String> skills = new ArrayList<>();
        for (int index = 1; index <= skillCount; index++) skills.add("기술" + index);
        List<String> expertise = rogueExpertise ? List.of("기술1", "기술2") : List.of();
        return new BuildCase(skills, expertise, numbered("소마법", cantripCount), numbered("주문", spellCount), subclass);
    }

    private static List<String> numbered(String prefix, int count) {
        List<String> values = new ArrayList<>();
        for (int index = 1; index <= count; index++) values.add(prefix + index);
        return values;
    }

    private static String buildJson(BuildCase buildCase) {
        Map<String, Object> build = new LinkedHashMap<>();
        build.put("schemaVersion", 2);
        build.put("subclass", buildCase.subclass());
        build.put("skillProficiencies", buildCase.skills());
        build.put("expertise", buildCase.expertise());
        build.put("equipmentSelections", Map.of("weapon", "default"));
        build.put("ruleChoices", Map.of());
        build.put("equippedItems", Map.of("armor", "", "shield", false));
        build.put("cantrips", buildCase.cantrips());
        build.put("learnedOrPreparedSpells", buildCase.spells());
        try {
            return JSON.writeValueAsString(build);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("test build serialization failed", exception);
        }
    }

    private record BuildCase(
            List<String> skills,
            List<String> expertise,
            List<String> cantrips,
            List<String> spells,
            String subclass) {}
}
