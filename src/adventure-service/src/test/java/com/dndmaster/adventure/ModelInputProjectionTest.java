package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.adventure.application.runtime.ModelInputProjection;
import com.dndmaster.adventure.application.runtime.RuntimeEvidence;
import com.dndmaster.adventure.application.runtime.RuntimeEvidenceType;
import com.dndmaster.adventure.application.runtime.StoryEvidenceVisibility;
import com.dndmaster.adventure.application.runtime.GmAgentRuntimePlanningAdapter;
import com.dndmaster.adventure.application.runtime.GmAgentPort;
import com.dndmaster.adventure.application.runtime.GmContextEnvelope;
import com.dndmaster.adventure.application.runtime.GmFinalValidator;
import com.dndmaster.adventure.application.runtime.GmPlanResult;
import com.dndmaster.adventure.application.runtime.RuntimePlan;
import com.dndmaster.adventure.application.runtime.EvidencePack;
import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ModelInputProjectionTest {
    @Test
    void excludes_hidden_evidence_and_secret_aliases_from_provider_text() {
        UUID document = UUID.randomUUID();
        RuntimeEvidence hidden = new RuntimeEvidence(RuntimeEvidenceType.STORYBOOK,
                new KnowledgeDocumentId(document), 3, "page:9", "trap-name:needle vault",
                StoryEvidenceVisibility.GM_ONLY, null, 0, List.of(), null);
        RuntimeEvidence visible = new RuntimeEvidence(RuntimeEvidenceType.STORYBOOK,
                new KnowledgeDocumentId(document), 3, "page:1", "the door is old",
                StoryEvidenceVisibility.PLAYER_VISIBLE, null, 0, List.of(), null);

        ModelInputProjection projection = ModelInputProjection.create(
                Set.of(document), List.of(hidden, visible), List.of(), List.of(),
                "checkpointSummary=needle vault", "public facts only", Set.of());

        assertTrue(projection.promptText().contains("the door is old"));
        assertFalse(projection.promptText().contains("needle vault"));
        assertFalse(projection.promptText().contains("checkpointSummary"));
        assertTrue(projection.audit().stream().anyMatch(a -> a.decision().equals("REJECTED_HIDDEN")));
    }

    @Test
    void fails_closed_for_scope_mismatch_and_missing_visibility() {
        UUID allowed = UUID.randomUUID();
        RuntimeEvidence mismatched = new RuntimeEvidence(RuntimeEvidenceType.RULEBOOK,
                new KnowledgeDocumentId(UUID.randomUUID()), 1, "page:1", "rule");
        assertThrows(IllegalArgumentException.class, () -> ModelInputProjection.create(
                Set.of(allowed), List.of(mismatched), List.of(), List.of(), "", "", Set.of()));

        RuntimeEvidence missingVisibility = new RuntimeEvidence(RuntimeEvidenceType.STORYBOOK,
                new KnowledgeDocumentId(allowed), 1, "page:1", "secret",
                null, null, 0, List.of(), null);
        assertFalse(ModelInputProjection.create(Set.of(allowed), List.of(missingVisibility), List.of(), List.of(), "", "", Set.of())
                .promptText().contains("secret"));
    }

    @Test
    void does_not_release_evidence_before_disclosure_turn() {
        UUID document = UUID.randomUUID();
        RuntimeEvidence gated = new RuntimeEvidence(RuntimeEvidenceType.STORYBOOK,
                new KnowledgeDocumentId(document), 1, "page:2", "revealed clue",
                StoryEvidenceVisibility.REVEALED_AFTER_EVENT, "DOOR_OPENED", 4, List.of(), null);
        assertFalse(ModelInputProjection.create(Set.of(document), List.of(gated), List.of(), List.of(), "", "",
                Set.of("DOOR_OPENED"), 3).promptText().contains("revealed clue"));
        assertTrue(ModelInputProjection.create(Set.of(document), List.of(gated), List.of(), List.of(), "", "",
                Set.of("DOOR_OPENED"), 4).promptText().contains("revealed clue"));
    }

    @Test
    void strict_projection_rejects_missing_or_mismatched_request_scope() {
        UUID document = UUID.randomUUID();
        RuntimeEvidence evidence = new RuntimeEvidence(RuntimeEvidenceType.RULEBOOK,
                new KnowledgeDocumentId(document), 1, "page:1", "rule")
                .withScope(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        assertThrows(IllegalArgumentException.class, () -> ModelInputProjection.createStrict(
                Set.of(document), java.util.Map.of(document, 1L), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                List.of(), List.of(evidence), List.of(), "", Set.of(), 0));
    }

    @Test
    void adapter_sends_projected_payload_not_legacy_request_fields() {
        UUID document = UUID.randomUUID();
        RuntimeEvidence hidden = new RuntimeEvidence(RuntimeEvidenceType.STORYBOOK,
                new KnowledgeDocumentId(document), 1, "page:9", "secret trap",
                StoryEvidenceVisibility.GM_ONLY, null, 0, List.of(), null);
        CapturingAgent agent = new CapturingAgent();
        var request = new com.dndmaster.adventure.application.runtime.RuntimePlanningRequest(
                new com.dndmaster.adventure.domain.adventure.AdventureId(UUID.randomUUID()),
                new com.dndmaster.adventure.domain.adventure.OwnerPlayerId(UUID.randomUUID()), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), 1, new com.dndmaster.adventure.domain.adventure.AdventureContext("scene", "npc", null, null),
                null, "open", new EvidencePack(List.of(hidden), List.of(), List.of()), List.of("secret trap"), List.of(),
                "secret trap");
        new GmAgentRuntimePlanningAdapter(agent, new GmFinalValidator()).plan(request);
        assertFalse(agent.context.evidencePack().storybook().stream().anyMatch(e -> e.excerpt().contains("secret trap")));
        assertFalse(agent.context.storyPlanContext().contains("secret trap"));
        assertEquals(List.of("secret trap"), request.recentTurns());
    }

    private static final class CapturingAgent implements GmAgentPort {
        GmContextEnvelope context;
        public GmPlanResult plan(GmContextEnvelope context) {
            this.context = context;
            return new GmPlanResult(new RuntimePlan("scene", null, "judgment", "narration", null, List.of(), List.of()), "p", "m", "r", List.of());
        }
    }
}
