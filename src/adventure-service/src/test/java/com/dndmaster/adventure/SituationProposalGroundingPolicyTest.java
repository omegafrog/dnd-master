package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndmaster.adventure.application.runtime.CompletionProposal;
import com.dndmaster.adventure.application.runtime.RuntimeEvidence;
import com.dndmaster.adventure.application.runtime.RuntimeEvidenceType;
import com.dndmaster.adventure.application.runtime.RuntimeResolutionProposal;
import com.dndmaster.adventure.application.runtime.SituationProposal;
import com.dndmaster.adventure.application.runtime.SituationProposalGroundingPolicy;
import com.dndmaster.adventure.application.runtime.SituationUpdateProposal;
import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import com.dndmaster.adventure.domain.runtime.DisclosureState;
import com.dndmaster.adventure.domain.runtime.GameStateDelta;
import com.dndmaster.adventure.domain.scenario.ScenarioModel;
import com.dndmaster.adventure.domain.scenario.ScenarioModelElement;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SituationProposalGroundingPolicyTest {
    @Test
    void accepts_a_scenario_supported_situation_before_consulting_rag() {
        var grounded = SituationProposalGroundingPolicy.ground(proposal(SituationProposal.Basis.SCENARIO, "cellar"),
                model(), List.of(), UUID.randomUUID());
        assertEquals("cellar", grounded.situationUpdate().location());
        assertEquals(List.of(), grounded.runtimeAddedFacts());
    }

    @Test
    void accepts_a_rag_supported_situation_when_no_scenario_element_is_applicable() {
        RuntimeEvidence evidence = new RuntimeEvidence(RuntimeEvidenceType.STORYBOOK,
                new KnowledgeDocumentId(UUID.randomUUID()), 1, "page-2", "Rats lurk in the cellar.", "story:cellar-rats");
        var grounded = SituationProposalGroundingPolicy.ground(proposal(SituationProposal.Basis.RAG, "story:cellar-rats"),
                ScenarioModel.empty(), List.of(evidence), UUID.randomUUID());
        assertEquals("rats are nearby", grounded.situationUpdate().threat());
        assertEquals(List.of(), grounded.runtimeAddedFacts());
    }

    @Test
    void creates_a_persisted_fallback_fact_only_when_the_gm_marks_it_required() {
        var grounded = SituationProposalGroundingPolicy.ground(proposal(SituationProposal.Basis.FALLBACK, ""),
                ScenarioModel.empty(), List.of(), UUID.randomUUID());
        assertEquals(1, grounded.runtimeAddedFacts().size());
        assertEquals("cellar", grounded.situationUpdate().location());
    }

    @Test
    void rejects_an_unverified_rag_reference() {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> SituationProposalGroundingPolicy.ground(proposal(SituationProposal.Basis.RAG, "missing"),
                        ScenarioModel.empty(), List.of(), UUID.randomUUID()));
        assertEquals("SITUATION_RAG_REFERENCE_REQUIRED", failure.getMessage());
    }

    private static RuntimeResolutionProposal proposal(SituationProposal.Basis basis, String reference) {
        var update = SituationUpdateProposal.transition("cellar", "find the source", "rats are nearby", "secure the room");
        return new RuntimeResolutionProposal(GameStateDelta.empty(), DisclosureState.empty(), update, List.of(),
                CompletionProposal.continueAdventure(), new SituationProposal(update, basis, reference, true));
    }

    private static ScenarioModel model() {
        return new ScenarioModel(1, List.of(), List.of(new ScenarioModelElement("cellar", "location", Map.of(), List.of())),
                List.of(), List.of(), List.of(), List.of(), List.of(), "The party arrives.");
    }
}
