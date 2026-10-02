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

    @Test
    void all_published_ranges_cover_their_originals_even_after_more_than_three_summaries() {
        AdventureId adventureId = AdventureId.generate();
        List<ConversationSummary> published = List.of(
                new ConversationSummary(adventureId, 1, 2, 3, 5, "첫 요약"),
                new ConversationSummary(adventureId, 2, 6, 7, 6, "둘째 요약"),
                new ConversationSummary(adventureId, 3, 10, 11, 7, "셋째 요약"),
                new ConversationSummary(adventureId, 4, 14, 15, 8, "넷째 요약"));
        assertTrue(RuntimeTurnApplicationService.coveredByPublishedSummary(new ConversationEntry(2, "PLAYER", "오래된 원문"), published));
        assertTrue(RuntimeTurnApplicationService.coveredByPublishedSummary(new ConversationEntry(15, "AI_GAME_MASTER", "최근 요약 원문"), published));
        assertFalse(RuntimeTurnApplicationService.coveredByPublishedSummary(new ConversationEntry(4, "PLAYER", "수동 처리 공백"), published));
    }
}
