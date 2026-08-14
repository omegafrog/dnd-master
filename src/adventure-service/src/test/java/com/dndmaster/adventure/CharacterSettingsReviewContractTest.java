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
    void distinguishes_no_proposals_extraction_failure_and_insufficient_evidence() {
        assertEquals(StorybookExtractionState.NO_PROPOSALS, StorybookExtractionState.NO_PROPOSALS);
        assertEquals(StorybookExtractionState.EXTRACTION_FAILED, StorybookExtractionState.EXTRACTION_FAILED);
        assertEquals(StorybookExtractionState.INSUFFICIENT_EVIDENCE, StorybookExtractionState.INSUFFICIENT_EVIDENCE);
    }

    @Test
    void keeps_legacy_blueprint_constructor_compatible_while_exposing_base_schema_contract() {
        var view = new CharacterCreationBlueprintView(true, "legacy", 1, 0, List.of());

        assertTrue(view.baseSchema() instanceof RulebookBaseSchemaView);
        assertTrue(view.storybookProposals().isEmpty());
        assertEquals(StorybookExtractionState.NO_PROPOSALS, view.storybookExtractionState());
    }
}
