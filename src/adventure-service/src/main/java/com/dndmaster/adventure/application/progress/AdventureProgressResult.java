package com.dndmaster.adventure.application.progress;

import com.dndmaster.adventure.domain.adventure.AdventureContext;
import com.dndmaster.adventure.domain.adventure.ConversationEntry;
import java.util.List;

public record AdventureProgressResult(AdventureContext context, List<ConversationEntry> conversation, long version) {
    public AdventureProgressResult {
        conversation = List.copyOf(conversation);
    }
}
