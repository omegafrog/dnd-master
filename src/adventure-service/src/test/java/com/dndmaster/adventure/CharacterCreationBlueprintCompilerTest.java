package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndmaster.adventure.application.scenario.blueprint.CharacterCreationBlueprintCompiler;
import com.dndmaster.adventure.application.scenario.blueprint.CharacterCreationBlueprintCompiler.FieldCandidate;
import com.dndmaster.adventure.domain.scenario.CharacterCreationBlueprintStatus;
import com.dndmaster.adventure.domain.scenario.InputMode;
import com.dndmaster.adventure.domain.scenario.ScenarioSourceReference;
import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CharacterCreationBlueprintCompilerTest {
    private static final ScenarioSourceReference HANDOUT = source(1);
    private static final ScenarioSourceReference RULEBOOK = source(2);
    private static final ScenarioSourceReference STORYBOOK = source(3);

    @Test
    void preservesExplicitInputModeSuggestionsAndSourceQuote() {
        var blueprint = new CharacterCreationBlueprintCompiler().compile(1, List.of(
                new FieldCandidate("name", List.of(), true, "RULEBOOK", RULEBOOK, "Name: Aria",
                        InputMode.FREE_TEXT, List.of("Aria")),
                new FieldCandidate("race", List.of("Elf", "Dwarf"), true, "RULEBOOK", RULEBOOK, "Race: Elf, Dwarf",
                        InputMode.SINGLE_SELECT, List.of()),
                new FieldCandidate("starting_ability_scores", List.of("STR", "DEX"), true, "RULEBOOK", RULEBOOK,
                        "Scores: STR, DEX", InputMode.MULTI_SELECT, List.of())));

        assertEquals(InputMode.FREE_TEXT, blueprint.field("name").inputMode());
        assertEquals(List.of("Aria"), blueprint.field("name").suggestions());
        assertEquals(InputMode.SINGLE_SELECT, blueprint.field("race").inputMode());
        assertEquals(InputMode.MULTI_SELECT, blueprint.field("starting_ability_scores").inputMode());
        assertEquals("Name: Aria", blueprint.field("name").sourceQuote());
    }

    @Test
    void resolvesMultipleSelectionsWithoutChangingInputMode() {
        var blueprint = new CharacterCreationBlueprintCompiler().compile(1, List.of(
                new FieldCandidate("starting_ability_scores", List.of("STR", "DEX"), true, "RULEBOOK", RULEBOOK,
                        "Scores", InputMode.MULTI_SELECT, List.of())));

        var resolved = blueprint.resolve("starting_ability_scores", "STR,DEX");

        assertEquals(List.of("STR", "DEX"), resolved.field("starting_ability_scores").options());
        assertEquals(InputMode.MULTI_SELECT, resolved.field("starting_ability_scores").inputMode());
    }

    @Test
    void resolvesFreeTextAndAcceptsLegacyFieldDefaults() {
        var blueprint = new CharacterCreationBlueprintCompiler().compile(1, List.of(
                new FieldCandidate("name", List.of(), false, "RULEBOOK", RULEBOOK, "Name")));

        var resolved = blueprint.resolve("name", "Aria");
        var legacy = new com.dndmaster.adventure.domain.scenario.CharacterCreationBlueprint.Field(
                "name", List.of(), true, "RULEBOOK", List.of(RULEBOOK), "EXTRACTED", List.of(), null, null, null);

        assertEquals(List.of("Aria"), resolved.field("name").options());
        assertEquals(InputMode.FREE_TEXT, resolved.field("name").inputMode());
        assertEquals(InputMode.FREE_TEXT, legacy.inputMode());
    }

    @Test
    void preservesStorybookSourceType() {
        var blueprint = new CharacterCreationBlueprintCompiler().compile(1, List.of(
                new FieldCandidate("race", List.of("Elf"), true, "STORYBOOK", RULEBOOK, "Race: Elf")));

        assertEquals("STORYBOOK", blueprint.field("race").sourceType());
    }

    @Test
    void storybookWinsOverRulebookAndRetainsConflictEvidence() {
        var blueprint = new CharacterCreationBlueprintCompiler().compile(1, List.of(
                new FieldCandidate("race", List.of("Elf"), true, "RULEBOOK", RULEBOOK, "Race: Elf"),
                new FieldCandidate("race", List.of("Tiefling"), true, "STORYBOOK", STORYBOOK, "Race: Tiefling")));

        assertEquals(List.of("Tiefling"), blueprint.field("race").options());
        assertEquals("STORYBOOK", blueprint.field("race").sourceType());
        assertEquals(CharacterCreationBlueprintStatus.NEEDS_REVIEW, blueprint.status());
        assertEquals(List.of(RULEBOOK, STORYBOOK), blueprint.field("race").evidence());
        assertEquals(List.of("conflicting storybook/rulebook values"), blueprint.field("race").diagnostics());
    }

    @Test
    void identifiesHandoutInStorybookConflict() {
        var blueprint = new CharacterCreationBlueprintCompiler().compile(1, List.of(
                new FieldCandidate("race", List.of("Human"), true, "HANDOUT", HANDOUT, "Race: Human"),
                new FieldCandidate("race", List.of("Tiefling"), true, "STORYBOOK", STORYBOOK, "Race: Tiefling")));

        assertEquals(List.of("conflicting storybook/handout values"), blueprint.field("race").diagnostics());
        assertEquals(List.of(HANDOUT, STORYBOOK), blueprint.field("race").evidence());
    }

    @Test
    void preservesStorybookOnlyDynamicFields() {
        var blueprint = new CharacterCreationBlueprintCompiler().compile(1, List.of(
                new FieldCandidate("faction", List.of("Harper"), true, "STORYBOOK", STORYBOOK,
                        "Faction: Harper")));

        assertEquals(List.of("faction"), blueprint.fields().stream().map(field -> field.key()).toList());
        assertEquals(List.of("Harper"), blueprint.field("faction").options());
    }

    @Test
    void handoutWinsOverRulebookAndMergesMultipleHandouts() {
        var blueprint = new CharacterCreationBlueprintCompiler().compile(3, List.of(
                new FieldCandidate("race", List.of("Elf"), true, "HANDOUT", HANDOUT, "Elf"),
                new FieldCandidate("class", List.of("Rogue"), true, "HANDOUT", HANDOUT, "Rogue"),
                new FieldCandidate("class", List.of("Wizard"), true, "RULEBOOK", RULEBOOK, "Wizard"),
                new FieldCandidate("background", List.of("Sage"), true, "HANDOUT", RULEBOOK, "Sage")));

        assertEquals(CharacterCreationBlueprintStatus.READY, blueprint.status());
        assertEquals(List.of("Rogue"), blueprint.field("class").options());
        assertEquals("HANDOUT", blueprint.field("class").sourceType());
        assertEquals(List.of("background", "class", "race"), blueprint.fields().stream().map(field -> field.key()).sorted().toList());
    }

    @Test
    void conflictingHandoutsRequireReviewAndRetainCandidates() {
        var blueprint = new CharacterCreationBlueprintCompiler().compile(1, List.of(
                new FieldCandidate("race", List.of("Elf"), true, "HANDOUT", HANDOUT, "Elf"),
                new FieldCandidate("race", List.of("Dwarf"), true, "HANDOUT", RULEBOOK, "Dwarf")));

        assertEquals(CharacterCreationBlueprintStatus.NEEDS_REVIEW, blueprint.status());
        assertEquals(List.of("Elf", "Dwarf"), blueprint.field("race").options());
        assertEquals(List.of("conflicting handout values"), blueprint.field("race").diagnostics());
    }

    @Test
    void missingExistingFieldRequiresManualInput() {
        var blueprint = new CharacterCreationBlueprintCompiler().compile(1, List.of(
                new FieldCandidate("level", List.of(), false, "RULEBOOK", RULEBOOK, "")));

        assertEquals(CharacterCreationBlueprintStatus.NEEDS_REVIEW, blueprint.status());
        assertEquals("MANUAL_INPUT_REQUIRED", blueprint.field("level").inputStatus());
    }

    @Test
    void usesExtractedRulebookValuesWhenHandoutHasNoValue() {
        var blueprint = new CharacterCreationBlueprintCompiler().compile(1, List.of(
                new FieldCandidate("race", List.of(), false, "HANDOUT", HANDOUT, ""),
                new FieldCandidate("race", List.of("Elf"), true, "RULEBOOK", RULEBOOK, "race: Elf")));

        assertEquals(CharacterCreationBlueprintStatus.READY, blueprint.status());
        assertEquals(List.of("Elf"), blueprint.field("race").options());
        assertEquals("RULEBOOK", blueprint.field("race").sourceType());
    }

    @Test
    void blankFieldKeyIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new CharacterCreationBlueprintCompiler().compile(1, List.of(
                new FieldCandidate(" ", List.of("Good"), true, "HANDOUT", HANDOUT, "Good"))));
    }

    @Test
    void reviewResolutionThenPublishCreatesNewImmutableRevision() {
        var draft = new CharacterCreationBlueprintCompiler().compile(1, List.of(
                new FieldCandidate("race", List.of("Elf"), true, "HANDOUT", HANDOUT, "Elf")));

        var resolved = draft.resolve("race", "Elf");
        var published = resolved.publish();

        assertEquals(CharacterCreationBlueprintStatus.READY, resolved.status());
        assertEquals(CharacterCreationBlueprintStatus.PUBLISHED, published.status());
        assertEquals(1, draft.revision());
        assertEquals(3, published.revision());
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> published.resolve("race", "Elf"));
    }

    private static ScenarioSourceReference source(long version) {
        return new ScenarioSourceReference(new KnowledgeDocumentId(UUID.randomUUID()), version, "p1");
    }
}
