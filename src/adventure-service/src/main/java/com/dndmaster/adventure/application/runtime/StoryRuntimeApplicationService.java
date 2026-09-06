package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.application.saved.AdventureRepository;
import com.dndmaster.adventure.domain.adventure.Adventure;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import com.dndmaster.adventure.domain.runtime.story.StoryRuntimeRules;
import com.dndmaster.adventure.domain.runtime.story.StoryRuntimeProposal;
import com.dndmaster.adventure.domain.runtime.story.StoryRuntimeState;
import com.dndmaster.adventure.domain.scenario.DetailedStage;
import com.dndmaster.adventure.domain.scenario.StageBackbone;
import java.util.Objects;

/** Coordinates deterministic story proposal admission with the Adventure aggregate commit. */
public final class StoryRuntimeApplicationService {
    private final AdventureRepository adventures;

    public StoryRuntimeApplicationService(AdventureRepository adventures) {
        this.adventures = Objects.requireNonNull(adventures, "adventure repository must not be null");
    }

    public Adventure apply(AdventureId adventureId, OwnerPlayerId owner, DetailedStage stage,
            StoryRuntimeProposal proposal) {
        Adventure adventure = adventures.findById(Objects.requireNonNull(adventureId, "adventure id must not be null"))
                .orElseThrow(() -> new IllegalStateException("adventure not found"));
        StoryRuntimeState state = adventure.storyRuntimeState();
        if (state == null) throw new IllegalStateException("story runtime state is not initialized");
        StoryRuntimeState next = StoryRuntimeRules.apply(state, stage, proposal);
        if (next == state) return adventure;
        adventure.commitStoryRuntimeState(Objects.requireNonNull(owner, "owner must not be null"), adventure.version(), next);
        adventures.save(adventure);
        return adventure;
    }

    /** Commits a stage transition in one aggregate write after next-stage preparation has succeeded. */
    public Adventure transition(AdventureId adventureId, OwnerPlayerId owner, DetailedStage current,
            DetailedStage next, StageBackbone backbone) {
        Adventure adventure = adventures.findById(Objects.requireNonNull(adventureId, "adventure id must not be null"))
                .orElseThrow(() -> new IllegalStateException("adventure not found"));
        StoryRuntimeState state = adventure.storyRuntimeState();
        if (state == null) throw new IllegalStateException("story runtime state is not initialized");
        StoryRuntimeState transitioned = StoryRuntimeRules.transition(state, current, next, backbone);
        adventure.commitStoryRuntimeState(Objects.requireNonNull(owner, "owner must not be null"), adventure.version(), transitioned);
        adventures.save(adventure);
        return adventure;
    }
}
