package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndmaster.adventure.application.runtime.EvidencePack;
import com.dndmaster.adventure.application.runtime.GmFinalValidator;
import com.dndmaster.adventure.application.runtime.GmPlanResult;
import com.dndmaster.adventure.application.runtime.RuntimeEvidence;
import com.dndmaster.adventure.application.runtime.RuntimeEvidenceType;
import com.dndmaster.adventure.application.runtime.RuntimePlan;
import com.dndmaster.adventure.application.runtime.StoryEvidenceVisibility;
import com.dndmaster.adventure.domain.adventure.AdventureContext;
import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GmAgentPolicyTest {
    private final KnowledgeDocumentId document = new KnowledgeDocumentId(UUID.randomUUID());
    private final RuntimeEvidence evidence = new RuntimeEvidence(
            RuntimeEvidenceType.RULEBOOK, document, 1, "page:1", "A rule");
    private final EvidencePack pack = new EvidencePack(List.of(), List.of(evidence), List.of());
    private final AdventureContext context = new AdventureContext("scene", "npc", null, null);

    @Test
    void accepts_only_citations_from_selected_evidence_and_empty_state_delta() {
        RuntimePlan plan = new RuntimePlan("scene", "npc", "judgment", "narration", null, List.of(evidence), List.of());
        assertDoesNotThrow(() -> new GmFinalValidator().validate(
                new GmPlanResult(plan, "ollama", "qwen3:8b", "reasoning", List.of()), pack, context, Set.of()));
    }

    @Test
    void rejects_uncited_claims_and_state_mutation() {
        RuntimePlan plan = new RuntimePlan("scene", "npc", "judgment", "narration", null, List.of(), List.of());
        assertThrows(IllegalStateException.class, () -> new GmFinalValidator().validate(
                new GmPlanResult(plan, "ollama", "qwen3:8b", "reasoning", List.of("hp: 1")), pack, context, Set.of()));
    }

    @Test
    void rejects_hidden_data_in_player_narration() {
        RuntimePlan plan = new RuntimePlan("scene", "npc", "judgment", "secret treasure", null, List.of(evidence), List.of());
        assertThrows(IllegalStateException.class, () -> new GmFinalValidator().validate(
                new GmPlanResult(plan, "ollama", "qwen3:8b", "reasoning", List.of()), pack, context, Set.of("secret treasure")));
    }

    @Test
    void rejects_gm_only_story_plan_context_in_player_narration() {
        RuntimePlan plan = new RuntimePlan("scene", "npc", "judgment", "stage=secret ending", null, List.of(evidence), List.of());
        assertThrows(IllegalStateException.class, () -> new GmFinalValidator().validate(
                new GmPlanResult(plan, "ollama", "qwen3:8b", "reasoning", List.of()), pack, context,
                Set.of("stage=secret ending")));
    }

    @Test
    void rejects_rule_claim_cited_only_to_story_evidence() {
        RuntimeEvidence story = new RuntimeEvidence(
                RuntimeEvidenceType.STORYBOOK, document, 1, "scene:1", "A locked door");
        RuntimePlan plan = new RuntimePlan("scene", "npc", "The rule says roll a check", "narration", null,
                List.of(story), List.of());
        assertThrows(IllegalStateException.class, () -> new GmFinalValidator().validate(
                new GmPlanResult(plan, "ollama", "qwen3:8b", "reasoning", List.of()),
                new EvidencePack(List.of(story), List.of(), List.of()), context, Set.of()));
    }

    @Test
    void rejects_outcome_claim_without_resolution_evidence() {
        RuntimePlan plan = new RuntimePlan("scene", "npc", "The attack hits", "The goblin takes damage", null,
                List.of(evidence), List.of());
        assertThrows(IllegalStateException.class, () -> new GmFinalValidator().validate(
                new GmPlanResult(plan, "ollama", "qwen3:8b", "reasoning", List.of()), pack, context, Set.of()));
    }

    @Test
    void rejects_outcome_claim_using_partial_or_conflicting_resolution() {
        RuntimeEvidence partial = new RuntimeEvidence(RuntimeEvidenceType.RESOLUTION, document, 1, "roll:1", "attack roll",
                StoryEvidenceVisibility.PLAYER_VISIBLE, null, 0, List.of("resolution-status=PARTIAL", "conflict: unresolved"), null);
        RuntimePlan plan = new RuntimePlan("scene", "npc", "The attack hits", "damage is applied", null,
                List.of(partial), List.of());
        assertThrows(IllegalStateException.class, () -> new GmFinalValidator().validate(
                new GmPlanResult(plan, "ollama", "qwen3:8b", "reasoning", List.of()),
                new EvidencePack(List.of(), List.of(), List.of(partial)), context, Set.of()));
    }

    @Test
    void rejects_player_narration_that_repeats_undisclosed_story_evidence() {
        RuntimeEvidence story = new RuntimeEvidence(
                RuntimeEvidenceType.STORYBOOK, document, 1, "scene:1", "The hidden chamber contains gold");
        RuntimePlan plan = new RuntimePlan("scene", "npc", "judgment", "The hidden chamber contains gold", null,
                List.of(story), List.of());
        assertThrows(IllegalStateException.class, () -> new GmFinalValidator().validate(
                new GmPlanResult(plan, "ollama", "qwen3:8b", "reasoning", List.of()),
                new EvidencePack(List.of(story), List.of(), List.of()), context, Set.of()));
    }

    @Test
    void rejects_paraphrased_hidden_story_fact_when_distinctive_tokens_leak() {
        RuntimePlan plan = new RuntimePlan("scene", "npc", "judgment", "A dragon waits beside the vault", null,
                List.of(evidence), List.of());
        assertThrows(IllegalStateException.class, () -> new GmFinalValidator().validate(
                new GmPlanResult(plan, "ollama", "qwen3:8b", "reasoning", List.of()), pack, context,
                Set.of("The dragon guards the hidden vault")));
    }
}
