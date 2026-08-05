package com.dndmaster.adventure.api;

import com.dndmaster.adventure.application.saved.AdventureRepository;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.ConversationEntry;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/adventures/{adventureId}/conversation")
public final class AdventureConversationController {
    private final AdventureRepository adventures;
    private final AuthenticatedPlayerResolver playerResolver;

    public AdventureConversationController(AdventureRepository adventures, AuthenticatedPlayerResolver playerResolver) {
        this.adventures = adventures;
        this.playerResolver = playerResolver;
    }

    @GetMapping
    ConversationView read(@PathVariable UUID adventureId) {
        var adventure = adventures.findById(new AdventureId(adventureId)).orElseThrow(() -> new IllegalArgumentException("adventure not found"));
        if (!adventure.ownerPlayerId().equals(new OwnerPlayerId(playerResolver.playerId()))) throw new SecurityException("adventure access denied");
        return new ConversationView(adventure.id().value(), adventure.version(), adventure.conversation().stream().map(EntryView::from).toList());
    }

    public record ConversationView(UUID adventureId, long version, List<EntryView> entries) {}
    public record EntryView(long sequence, String speaker, String content) {
        static EntryView from(ConversationEntry entry) { return new EntryView(entry.sequence(), entry.speaker(), entry.content()); }
    }
}
