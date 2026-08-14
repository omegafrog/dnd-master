package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.adventure.application.scenario.preparation.CharacterCreationBlueprintView;
import com.dndmaster.adventure.application.scenario.preparation.CharacterCreationBlueprintView.RulebookBaseSchemaView;
import com.dndmaster.adventure.application.scenario.preparation.CharacterCreationBlueprintView.StorybookExtractionState;
import com.dndmaster.adventure.application.scenario.preparation.CharacterCreationBlueprintView.StorybookProposalView;
import java.util.List;
import org.junit.jupiter.api.Test;

class CharacterSettingsReviewContractTest {
    @Test
    void exposes_explicit_proposal_identity_source_evidence_and_readiness_without_diagnostics() {
        var source = new StorybookProposalView.SourceDocument("doc-1", "campaign.pdf", 3);
        var evidence = new StorybookProposalView.SourceEvidence("page:4", "Only elves may enter the grove.");
        var proposal = new StorybookProposalView("proposal-1", "race", "종족", "엘프만 선택 가능",
                source, "Only elves may enter the grove.", List.of(evidence), "UNDECIDED", "READY");

        assertEquals("proposal-1", proposal.proposalId());
        assertEquals(source, proposal.sourceDocument());
        assertEquals("Only elves may enter the grove.", proposal.sourceQuote());
        assertEquals("UNDECIDED", proposal.decisionState());
        assertEquals("READY", proposal.readinessState());
    }

    @Test
    void keeps_legacy_blueprint_constructor_compatible_while_exposing_base_schema_contract() {
        var view = new CharacterCreationBlueprintView(true, "legacy", 1, 0, List.of());

        assertTrue(view.baseSchema() instanceof RulebookBaseSchemaView);
        assertTrue(view.storybookProposals().isEmpty());
        assertEquals(StorybookExtractionState.NO_PROPOSALS, view.storybookExtractionState());
    }

    @Test
    void includes_template_fields_in_base_schema_but_excludes_overlay_sources() {
        var template = new CharacterCreationBlueprintView.FieldView("race", List.of("Elf"), true,
                "TEMPLATE", "EXTRACTED", List.of());
        var storybook = new CharacterCreationBlueprintView.FieldView("alignment", List.of("Grove-bound"), true,
                "STORYBOOK", "CONFLICT_REVIEW", List.of());
        var handout = new CharacterCreationBlueprintView.FieldView("faction", List.of("Wardens"), true,
                "HANDOUT", "CONFLICT_REVIEW", List.of());

        assertEquals(List.of("race"), RulebookBaseSchemaView.from(List.of(template, storybook, handout)).fields().stream()
                .map(CharacterCreationBlueprintView.FieldView::key).toList());
        assertTrue(new RulebookBaseSchemaView("DND_5E_2014", List.of(handout)).fields().isEmpty());
    }

    @Test
    void keeps_proposal_identity_when_source_text_changes() {
        var first = new StorybookProposalView(StorybookProposalView.stableId("doc-1", 3, "race"), "race",
                "Race", "Elf only", new StorybookProposalView.SourceDocument("doc-1", "story.pdf", 3),
                "Only elves.", List.of(), "UNDECIDED", "READY");
        var changedText = new StorybookProposalView(StorybookProposalView.stableId("doc-1", 3, "race"), "race",
                "Race", "Elf or dwarf", new StorybookProposalView.SourceDocument("doc-1", "story.pdf", 3),
                "Elves or dwarves.", List.of(), "UNDECIDED", "READY");

        assertEquals(first.proposalId(), changedText.proposalId());
    }

    @Test
    void disambiguates_duplicate_unresolved_field_keys_deterministically() {
        String first = StorybookProposalView.stableId("UNRESOLVED", 0, "race", "race|true|SINGLE_SELECT|Elf|");
        String second = StorybookProposalView.stableId("UNRESOLVED", 0, "race", "race|true|SINGLE_SELECT|Dwarf|");

        assertTrue(!first.equals(second));
        assertEquals(first, StorybookProposalView.stableId("UNRESOLVED", 0, "race", "race|true|SINGLE_SELECT|Elf|"));
    }

    @Test
    void preserves_edition_in_the_explicit_base_schema_projection() {
        var view = new CharacterCreationBlueprintView(true, "2024", 1, 0, List.of(), 1, List.of(),
                "READY", List.of(), "DND_5E_2024");

        assertEquals("DND_5E_2024", view.edition());
        assertEquals("DND_5E_2024", view.baseSchema().edition());
    }
}
