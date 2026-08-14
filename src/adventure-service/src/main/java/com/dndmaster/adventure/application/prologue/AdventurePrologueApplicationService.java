package com.dndmaster.adventure.application.prologue;

import com.dndmaster.adventure.application.saved.AdventureRepository;
import com.dndmaster.adventure.application.runtime.CharacterSheetReadPort;
import com.dndmaster.adventure.application.storyplan.AdventureStoryPlanRepository;
import com.dndmaster.adventure.domain.adventure.Adventure;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.ConversationEntry;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import com.dndmaster.adventure.domain.adventure.SessionId;
import com.dndmaster.adventure.application.runtime.GmAgentPort;
import com.dndmaster.adventure.application.runtime.GmContextEnvelope;
import com.dndmaster.adventure.application.runtime.EvidencePack;
import java.util.ArrayList;
import java.util.Objects;

public final class AdventurePrologueApplicationService {
    private final AdventureRepository adventures;
    private final AdventureStoryPlanRepository plans;
    private final CharacterSheetReadPort sheets;
    private final AdventurePrologueGenerationPort generator;
    private final GmAgentPort gmAgent;

    public AdventurePrologueApplicationService(AdventureRepository adventures, AdventureStoryPlanRepository plans,
            CharacterSheetReadPort sheets, AdventurePrologueGenerationPort generator) {
        this(adventures, plans, sheets, generator, null);
    }

    public AdventurePrologueApplicationService(AdventureRepository adventures, AdventureStoryPlanRepository plans,
            CharacterSheetReadPort sheets, AdventurePrologueGenerationPort generator, GmAgentPort gmAgent) {
        this.adventures = Objects.requireNonNull(adventures);
        this.plans = Objects.requireNonNull(plans);
        this.sheets = Objects.requireNonNull(sheets);
        this.generator = Objects.requireNonNull(generator);
        this.gmAgent = gmAgent;
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
        String narration = gmAgent == null ? generator.generate(new AdventurePrologueGenerationPort.Request(stage, party, evidence))
                : gmAgent.plan(new GmContextEnvelope(
                        adventure.id(), owner, adventure.sessionId().value(), java.util.UUID.randomUUID(),
                        adventure.scenarioId().value(), 0, adventure.currentContext(), null,
                        "모험의 첫 장면을 한국어로 생생하게 열어 주세요. 장소: " + stage.location() +
                                ", 목표: " + stage.goal() + ", 갈등: " + stage.conflict() +
                                ". 단서: " + String.join(", ", stage.npcOrClues()),
                        new EvidencePack(java.util.List.of(), java.util.List.of(), java.util.List.of()),
                        java.util.List.of(), party.stream().map(value -> value.name() + " (레벨 " + value.level() + ")").toList(),
                        "첫 단계: " + stage.title(), "", "", ""))
                        .plan().narration();
        narration = Objects.requireNonNull(narration);
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
