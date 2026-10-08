package com.dndmaster.adventure.api;

import com.dndmaster.adventure.application.saved.AdventureRepository;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.ConversationEntry;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import com.dndmaster.adventure.application.runtime.PlayerRollRequest;
import com.dndmaster.adventure.application.runtime.RuntimeTurnLifecycle;
import com.dndmaster.adventure.application.runtime.RuntimeTurnRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/adventures/{adventureId}/conversation")
public final class AdventureConversationController {
    private final AdventureRepository adventures;
    private final AuthenticatedPlayerResolver playerResolver;
    private final RuntimeTurnRepository runtimeTurns;

    public AdventureConversationController(AdventureRepository adventures, AuthenticatedPlayerResolver playerResolver,
            RuntimeTurnRepository runtimeTurns) {
        this.adventures = adventures;
        this.playerResolver = playerResolver;
        this.runtimeTurns = runtimeTurns;
    }

    @GetMapping
    ConversationView read(@PathVariable UUID adventureId) {
        var adventure = adventures.findById(new AdventureId(adventureId)).orElseThrow(() -> new IllegalArgumentException("adventure not found"));
        if (!adventure.ownerPlayerId().equals(new OwnerPlayerId(playerResolver.playerId()))) throw new SecurityException("adventure access denied");
        var turns = runtimeTurns.findAllByAdventureId(adventure.id());
        var pendingTurn = turns.stream()
                .filter(turn -> turn.lifecycle() == RuntimeTurnLifecycle.PENDING_ROLL)
                .findFirst()
                .orElse(null);
        PlayerRollRequest pendingRoll = pendingTurn == null ? null
                : new PlayerRollRequest(pendingTurn.turnId(), pendingTurn.plan().checkProposal().abilityOrSkill(),
                        pendingTurn.plan().checkProposal().diceExpression(), pendingTurn.plan().checkProposal().reason(),
                        pendingTurn.expectedVersion() == null ? adventure.version() : pendingTurn.expectedVersion());
        var conversation = pendingTurn == null ? adventure.conversation() : pendingTurn.conversation();
        var internalJudgments = turns.stream().map(turn -> turn.plan().judgment())
                .collect(java.util.stream.Collectors.toSet());
        return new ConversationView(adventure.id().value(), adventure.version(), adventure.currentContext().currentScene(),
                conversation.stream()
                        .filter(entry -> !"AI_GAME_MASTER".equals(entry.speaker()) || !internalJudgments.contains(entry.content()))
                        .map(EntryView::from).toList(), pendingRoll);
    }

    public record ConversationView(UUID adventureId, long version, String currentScene, List<EntryView> entries,
            PlayerRollRequest pendingRoll) {}
    public record EntryView(long sequence, String speaker, String content) {
        static EntryView from(ConversationEntry entry) { return new EntryView(entry.sequence(), entry.speaker(), entry.content()); }
    }
}
