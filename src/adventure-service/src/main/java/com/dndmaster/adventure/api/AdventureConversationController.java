package com.dndmaster.adventure.api;

import com.dndmaster.adventure.application.saved.AdventureRepository;
import com.dndmaster.adventure.application.runtime.PlayerProjection;
import com.dndmaster.adventure.application.runtime.RuntimeTurnRepository;
import com.dndmaster.adventure.application.runtime.SessionEventRepository;
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
    private final RuntimeTurnRepository turns;
    private final SessionEventRepository events;

    public AdventureConversationController(AdventureRepository adventures, AuthenticatedPlayerResolver playerResolver) {
        this(adventures, playerResolver, null, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public AdventureConversationController(AdventureRepository adventures, AuthenticatedPlayerResolver playerResolver,
            RuntimeTurnRepository turns, SessionEventRepository events) {
        this.adventures = adventures; this.playerResolver = playerResolver; this.turns = turns; this.events = events;
    }

    @GetMapping
    ConversationView read(@PathVariable UUID adventureId) {
        var adventure = adventures.findById(new AdventureId(adventureId)).orElseThrow(() -> new IllegalArgumentException("adventure not found"));
        if (!adventure.ownerPlayerId().equals(new OwnerPlayerId(playerResolver.playerId()))) throw new SecurityException("adventure access denied");
        var runtimeTurns = turns == null ? List.<com.dndmaster.adventure.application.runtime.RuntimeTurn>of()
                : turns.findAllByAdventureId(adventure.id());
        var committedEvents = events == null ? java.util.Set.<String>of() : events.after(adventure.sessionId().value(), -1).stream()
                .flatMap(event -> java.util.stream.Stream.of(event.type(), event.payload())).collect(java.util.stream.Collectors.toSet());
        return new ConversationView(adventure.id().value(), adventure.version(), adventure.conversation().stream()
                .map(entry -> {
                    var matchingTurns = runtimeTurns.stream().filter(turn -> turn.conversation().stream()
                            .anyMatch(item -> item.sequence() == entry.sequence())).toList();
                    if (entry.speaker().equals("AI_GAME_MASTER") && matchingTurns.isEmpty()) {
                        return EntryView.from(entry, "공개할 수 있는 장면 정보가 없습니다.");
                    }
                    String content = entry.content();
                    for (var turn : matchingTurns) content = PlayerProjection.redact(content, turn.evidencePack().all(),
                            committedEvents, turn.version(), turn.sessionId(), turn.scenarioPackageId(), adventure.ownerPlayerId().value());
                    return EntryView.from(entry, content);
                }).toList());
    }

    public record ConversationView(UUID adventureId, long version, List<EntryView> entries) {}
    public record EntryView(long sequence, String speaker, String content) {
        static EntryView from(ConversationEntry entry) { return from(entry, entry.content()); }
        static EntryView from(ConversationEntry entry, String content) { return new EntryView(entry.sequence(), entry.speaker(), content); }
    }
}
