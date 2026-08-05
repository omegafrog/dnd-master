package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndmaster.adventure.application.runtime.EvidencePack;
import com.dndmaster.adventure.application.runtime.GmFinalValidator;
import com.dndmaster.adventure.application.runtime.GmPlanResult;
import com.dndmaster.adventure.application.runtime.RuntimeEvidence;
import com.dndmaster.adventure.application.runtime.RuntimeEvidenceType;
import com.dndmaster.adventure.application.runtime.RuntimePlan;
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
}
