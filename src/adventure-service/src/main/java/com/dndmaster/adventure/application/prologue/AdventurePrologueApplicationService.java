package com.dndmaster.adventure.application.prologue;

import com.dndmaster.adventure.application.saved.AdventureRepository;
import com.dndmaster.adventure.application.runtime.CharacterSheetReadPort;
import com.dndmaster.adventure.application.runtime.PlayerProjection;
import com.dndmaster.adventure.application.storyplan.AdventureStoryPlanRepository;
import com.dndmaster.adventure.domain.adventure.Adventure;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.ConversationEntry;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import com.dndmaster.adventure.domain.adventure.SessionId;
import java.util.ArrayList;
import java.util.Objects;

public final class AdventurePrologueApplicationService {
    private final AdventureRepository adventures;
    private final AdventureStoryPlanRepository plans;
    private final CharacterSheetReadPort sheets;
    private final AdventurePrologueGenerationPort generator;

    public AdventurePrologueApplicationService(AdventureRepository adventures, AdventureStoryPlanRepository plans,
            CharacterSheetReadPort sheets, AdventurePrologueGenerationPort generator) {
        this.adventures = Objects.requireNonNull(adventures);
        this.plans = Objects.requireNonNull(plans);
        this.sheets = Objects.requireNonNull(sheets);
        this.generator = Objects.requireNonNull(generator);
    }

    public Adventure ensure(AdventureId adventureId, OwnerPlayerId owner) {
        Adventure adventure = adventures.findById(adventureId).orElseThrow(() -> new IllegalArgumentException("adventure not found"));
        if (!adventure.ownerPlayerId().equals(owner)) throw new SecurityException("adventure access denied");
        if (!adventure.conversation().isEmpty()) return adventure;
        var plan = plans.findBySessionId(adventure.sessionId()).orElseThrow(() -> new IllegalStateException("adventure story plan not found"));
        var stage = plan.stages().get(plan.currentStage());
        var party = adventure.party().stream().map(member -> {
            var sheet = sheets.read(member.characterSheetId());
            return new AdventurePrologueGenerationPort.CharacterSnapshot(sheet.name(), sheet.level());
        }).toList();
        var evidence = java.util.stream.Stream.concat(stage.npcOrClues().stream(), stage.endingIds().stream())
                .map(value -> "story-plan:stage-" + stage.position() + ":" + value).toList();
        String narration = PlayerProjection.redact(
                Objects.requireNonNull(generator.generate(new AdventurePrologueGenerationPort.Request(stage, party, evidence))),
                java.util.stream.Stream.concat(stage.npcOrClues().stream(), stage.endingIds().stream()).collect(java.util.stream.Collectors.toUnmodifiableSet()));
        var conversation = new ArrayList<>(adventure.conversation());
        conversation.add(new ConversationEntry(0, "AI_GAME_MASTER", narration));
        adventure.preserveProgress(owner, adventure.version(), adventure.currentContext(), conversation);
        try {
            adventures.save(adventure);
        } catch (com.dndmaster.adventure.infrastructure.persistence.OptimisticAdventureLockException concurrentRecovery) {
            return adventures.findById(adventureId).orElseThrow(() -> concurrentRecovery);
        }
        return adventure;
    }
}
