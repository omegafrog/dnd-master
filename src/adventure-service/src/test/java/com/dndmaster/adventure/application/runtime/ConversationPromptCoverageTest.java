package com.dndmaster.adventure.application.runtime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.ConversationEntry;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConversationPromptCoverageTest {
    @Test
    void excludes_only_entries_covered_by_a_published_summary() {
        AdventureId adventureId = AdventureId.generate();
        // A failed [0,1] job has no summary. A later published [2,3] range must not hide it.
        List<ConversationSummary> published = List.of(new ConversationSummary(adventureId, 1, 2, 3, 5, "요약"));
        assertFalse(RuntimeTurnApplicationService.coveredByPublishedSummary(new ConversationEntry(0, "PLAYER", "실패한 원문"), published));
        assertFalse(RuntimeTurnApplicationService.coveredByPublishedSummary(new ConversationEntry(1, "AI_GAME_MASTER", "실패한 응답"), published));
        assertTrue(RuntimeTurnApplicationService.coveredByPublishedSummary(new ConversationEntry(2, "PLAYER", "요약된 원문"), published));
        assertTrue(RuntimeTurnApplicationService.coveredByPublishedSummary(new ConversationEntry(3, "AI_GAME_MASTER", "요약된 응답"), published));
    }
}
