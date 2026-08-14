package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import com.dndmaster.adventure.domain.scenario.CharacterCreationBlueprint;
import com.dndmaster.adventure.domain.scenario.CharacterCreationBlueprintStatus;
import com.dndmaster.adventure.domain.scenario.ProposalDecisionState;
import com.dndmaster.adventure.domain.scenario.ScenarioSourceReference;
import com.dndmaster.adventure.domain.scenario.StorybookProposalDecision;
import com.dndmaster.adventure.domain.scenario.StorybookProposalEvidenceRequiredException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StorybookProposalDecisionTest {
    @Test
    void applies_only_selected_storybook_proposals_and_keeps_rulebook_base_fields() {
        var blueprint = blueprint();

        var decided = blueprint.decideProposal("proposal-applied", ProposalDecisionState.APPLIED, true);

        assertEquals(5, decided.revision());
        assertEquals(List.of("race", "alignment"), decided.appliedProjection().stream()
                .map(CharacterCreationBlueprint.Field::key).toList());
        assertEquals(1, decided.unresolvedProposalCount());
        assertEquals(ProposalDecisionState.APPLIED, decided.decision("proposal-applied").state());
        assertEquals(ProposalDecisionState.EXCLUDED, decided.decision("proposal-excluded").state());
    }

    @Test
    void rejects_use_when_the_proposal_has_no_evidence() {
        assertThrows(StorybookProposalEvidenceRequiredException.class,
                () -> blueprint().decideProposal("proposal-applied", ProposalDecisionState.APPLIED, false));
    }

    private static CharacterCreationBlueprint blueprint() {
        var source = new ScenarioSourceReference(
                new KnowledgeDocumentId(UUID.fromString("11111111-1111-1111-1111-111111111111")), 3, "page:4");
        return new CharacterCreationBlueprint(
                4,
                CharacterCreationBlueprintStatus.NEEDS_REVIEW,
                List.of(
                        new CharacterCreationBlueprint.Field("race", List.of("Elf"), true, "RULEBOOK", List.of(), "EXTRACTED", List.of()),
                        new CharacterCreationBlueprint.Field("alignment", List.of("Lawful Good"), true, "STORYBOOK", List.of(source), "EXTRACTED", List.of()),
                        new CharacterCreationBlueprint.Field("faction", List.of("Wardens"), true, "STORYBOOK", List.of(source), "EXTRACTED", List.of()),
                        new CharacterCreationBlueprint.Field("origin", List.of("Grove"), true, "STORYBOOK", List.of(source), "EXTRACTED", List.of())),
                List.of(),
                com.dndmaster.adventure.domain.scenario.BlueprintProvenance.empty(),
                List.of(
                        new StorybookProposalDecision("proposal-applied", "alignment", ProposalDecisionState.UNDECIDED),
                        new StorybookProposalDecision("proposal-excluded", "faction", ProposalDecisionState.EXCLUDED),
                        new StorybookProposalDecision("proposal-unresolved", "origin", ProposalDecisionState.UNDECIDED)));
    }
}
