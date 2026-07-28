package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndmaster.adventure.application.scenario.blueprint.CharacterCreationBlueprintCompiler;
import com.dndmaster.adventure.application.scenario.blueprint.CharacterCreationBlueprintCompiler.FieldCandidate;
import com.dndmaster.adventure.domain.scenario.CharacterCreationBlueprintStatus;
import com.dndmaster.adventure.domain.scenario.ScenarioSourceReference;
import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CharacterCreationBlueprintCompilerTest {
    private static final ScenarioSourceReference HANDOUT = source(1);
    private static final ScenarioSourceReference RULEBOOK = source(2);

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
    void customFieldIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new CharacterCreationBlueprintCompiler().compile(1, List.of(
                new FieldCandidate("alignment", List.of("Good"), true, "HANDOUT", HANDOUT, "Good"))));
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
