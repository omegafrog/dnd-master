package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dndmaster.adventure.application.runtime.RuntimeEvidence;
import com.dndmaster.adventure.application.runtime.RuntimeEvidenceType;
import com.dndmaster.adventure.application.runtime.StoryEvidenceVisibility;
import com.dndmaster.adventure.application.runtime.PlayerVisibleStoryEvidence;
import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StoryEvidenceVisibilityTest {
    @Test
    void projection_blocks_private_and_unrevealed_story_chunks() {
        var visible = evidence("visible", StoryEvidenceVisibility.PLAYER_VISIBLE, null, 0);
        var secret = evidence("secret", StoryEvidenceVisibility.GM_ONLY, null, 0);
        var gated = evidence("gated", StoryEvidenceVisibility.REVEALED_AFTER_EVENT, "door-opened", 4);

        assertEquals(List.of(visible), PlayerVisibleStoryEvidence.project(
                List.of(visible, secret, gated), Set.of(), 3));
        assertEquals(List.of(visible, gated), PlayerVisibleStoryEvidence.project(
                List.of(visible, secret, gated), Set.of("door-opened"), 4));
    }

    @Test
    void projection_preserves_provenance_and_non_story_evidence_is_not_projected() {
        var story = evidence("page:7", StoryEvidenceVisibility.PUBLIC_SUMMARY, null, 0);
        var rule = new RuntimeEvidence(RuntimeEvidenceType.RULEBOOK, new KnowledgeDocumentId(UUID.randomUUID()),
                2, "page:1", "rule");
        var projected = PlayerVisibleStoryEvidence.project(List.of(story, rule), Set.of(), 1);

        assertEquals(1, projected.size());
        assertEquals(story.knowledgeDocumentId(), projected.getFirst().knowledgeDocumentId());
        assertEquals(story.locator(), projected.getFirst().locator());
        assertEquals(story.extractionVersion(), projected.getFirst().extractionVersion());
    }

    @Test
    void hidden_excerpt_is_never_returned_in_public_narration() {
        var secret = evidence("secret", StoryEvidenceVisibility.GM_ONLY, null, 0);
        assertEquals("공개할 수 있는 장면 정보가 없습니다.", PlayerVisibleStoryEvidence.redactNarration(
                "A hidden excerpt", List.of(secret), Set.of(), 1));
    }

    @Test
    void hidden_story_terms_are_redacted_even_when_narration_paraphrases_excerpt() {
        var secret = new RuntimeEvidence(RuntimeEvidenceType.STORYBOOK, new KnowledgeDocumentId(UUID.randomUUID()),
                3, "secret", "sealed moon door", StoryEvidenceVisibility.GM_ONLY, null, 0);

        assertEquals("공개할 수 있는 장면 정보가 없습니다.", PlayerVisibleStoryEvidence.redactNarration(
                "The sealed stone door opens beneath the moon.", List.of(secret), Set.of(), 1));
    }

    private static RuntimeEvidence evidence(String locator, StoryEvidenceVisibility visibility, String event, long turn) {
        return new RuntimeEvidence(RuntimeEvidenceType.STORYBOOK, new KnowledgeDocumentId(UUID.randomUUID()),
                3, locator, "excerpt", visibility, event, turn);
    }
}
