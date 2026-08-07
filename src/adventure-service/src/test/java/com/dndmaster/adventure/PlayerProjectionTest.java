package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.adventure.application.runtime.PlayerProjection;
import com.dndmaster.adventure.application.runtime.RuntimeEvidence;
import com.dndmaster.adventure.application.runtime.RuntimeEvidenceType;
import com.dndmaster.adventure.application.runtime.StoryEvidenceVisibility;
import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlayerProjectionTest {
    @Test
    void one_policy_rejects_hidden_values_in_every_public_channel() {
        UUID document = UUID.randomUUID();
        RuntimeEvidence hidden = new RuntimeEvidence(RuntimeEvidenceType.STORYBOOK,
                new KnowledgeDocumentId(document), 1, "page:9", "DC 13 hidden ending",
                StoryEvidenceVisibility.GM_ONLY, null, 0);
        UUID session = UUID.randomUUID();
        UUID packageId = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        hidden = hidden.withScope(owner, session, packageId);
        RuntimeEvidence publicEvidence = new RuntimeEvidence(RuntimeEvidenceType.STORYBOOK,
                new KnowledgeDocumentId(document), 1, "page:1", "the bell rings",
                StoryEvidenceVisibility.PLAYER_VISIBLE, null, 0).withScope(owner, session, packageId);

        PlayerProjection projection = PlayerProjection.create(
                "the bell rings", "DC 13 hidden ending", "hidden ending",
                List.of("storybook:page:9", "storybook:page:1", "document:" + document),
                List.of("degraded-mode:RULE;secret=DC 13"),
                List.of("{\"warning\":\"DC 13\",\"result\":\"hidden ending\"}"),
                List.of(hidden, publicEvidence), Set.of(), 0, session, packageId, owner);

        assertEquals("the bell rings", projection.narration());
        assertEquals("공개할 수 있는 장면 정보가 없습니다.", projection.judgment());
        assertEquals("공개할 수 있는 장면 정보가 없습니다.", projection.currentScene());
        assertEquals(List.of("storybook:page:1"), projection.citations());
        assertTrue(projection.warnings().isEmpty());
        assertTrue(projection.toolResults().isEmpty());
        assertFalse(projection.toString().contains("DC 13"));
        assertFalse(projection.toString().contains("hidden ending"));
    }

    @Test
    void disclosed_evidence_preserves_public_provenance() {
        UUID document = UUID.randomUUID();
        RuntimeEvidence evidence = new RuntimeEvidence(RuntimeEvidenceType.STORYBOOK,
                new KnowledgeDocumentId(document), 2, "chapter:3", "the gate opens",
                StoryEvidenceVisibility.REVEALED_AFTER_EVENT, "GATE_OPENED", 2)
                .withScope(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

        PlayerProjection projection = PlayerProjection.create("the gate opens", "safe", "safe",
                List.of("storybook:chapter:3"), List.of(), List.of("safe tool result"),
                List.of(evidence), Set.of("GATE_OPENED"), 2, evidence.sessionId(), evidence.scenarioPackageId(), evidence.ownerPlayerId());

        assertEquals(List.of("storybook:chapter:3"), projection.citations());
        assertEquals(List.of("safe tool result"), projection.toolResults());
    }

    @Test
    void rejects_hidden_rule_evidence_and_scope_mismatch_at_publication() {
        UUID session = UUID.randomUUID();
        UUID packageId = UUID.randomUUID();
        RuntimeEvidence hiddenRule = new RuntimeEvidence(RuntimeEvidenceType.RULEBOOK,
                new KnowledgeDocumentId(UUID.randomUUID()), 4, "page:13", "DC 13 secret rule",
                StoryEvidenceVisibility.GM_ONLY, null, 0).withScope(UUID.randomUUID(), session, packageId);

        PlayerProjection projection = PlayerProjection.create("DC 13 secret rule", "safe", "safe",
                List.of("RULEBOOK:page:13"), List.of("warning=DC 13"), List.of("DC 13 secret rule"),
                List.of(hiddenRule), Set.of(), 0, session, packageId, hiddenRule.ownerPlayerId());

        assertEquals("공개할 수 있는 장면 정보가 없습니다.", projection.narration());
        assertTrue(projection.citations().isEmpty());
        assertTrue(projection.warnings().isEmpty());
        assertTrue(projection.toolResults().isEmpty());
    }
}
