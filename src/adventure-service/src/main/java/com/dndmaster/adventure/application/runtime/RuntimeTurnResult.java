package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.adventure.AdventureContext;
import com.dndmaster.adventure.domain.adventure.ConversationEntry;
import java.util.List;

// 턴 처리 후 세션에 남는 최종 결과다.
public record RuntimeTurnResult(RuntimeTurn turn, AdventureContext context, List<ConversationEntry> conversation, long version,
                                PlayerVisibleTurn visibleTurn) {
    public RuntimeTurnResult {
        conversation = List.copyOf(conversation);
        visibleTurn = visibleTurn == null ? new PlayerVisibleTurn("", context.currentScene(), List.of(), null) : visibleTurn;
    }

    public RuntimeTurnResult(RuntimeTurn turn, AdventureContext context, List<ConversationEntry> conversation, long version) {
        this(turn, context, conversation, version, null);
    }
}
