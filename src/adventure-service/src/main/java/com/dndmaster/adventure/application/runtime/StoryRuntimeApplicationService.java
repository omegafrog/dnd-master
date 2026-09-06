package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.application.saved.AdventureRepository;
import com.dndmaster.adventure.domain.adventure.Adventure;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import com.dndmaster.adventure.domain.runtime.story.StoryRuntimeRules;
import com.dndmaster.adventure.domain.runtime.story.StoryRuntimeProposal;
import com.dndmaster.adventure.domain.runtime.story.StoryRuntimeState;
import com.dndmaster.adventure.domain.runtime.story.FutureSuffixReplanningPolicy;
import com.dndmaster.adventure.domain.runtime.story.PremiseInvalidationPolicy;
import com.dndmaster.adventure.domain.runtime.story.PremiseInvalidationProposal;
import com.dndmaster.adventure.domain.runtime.story.PremiseInvalidationResult;
import com.dndmaster.adventure.domain.scenario.DetailedStage;
import com.dndmaster.adventure.domain.scenario.StageBackbone;
import com.dndmaster.adventure.domain.scenario.StageArtifactRepository;
import com.dndmaster.adventure.domain.scenario.StageBackboneEntry;
import java.util.List;
import java.util.Objects;

/** Coordinates deterministic story proposal admission with the Adventure aggregate commit. */
public final class StoryRuntimeApplicationService {
    private final AdventureRepository adventures;
    private final StageArtifactRepository artifacts;

    public StoryRuntimeApplicationService(AdventureRepository adventures) {
        this(adventures, null);
    }

    public StoryRuntimeApplicationService(AdventureRepository adventures, StageArtifactRepository artifacts) {
        this.adventures = Objects.requireNonNull(adventures, "adventure repository must not be null");
        this.artifacts = artifacts;
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

    /** Verifies and records a selective future replan before publishing its immutable new backbone. */
    public StoryRuntimeReplanResult replan(AdventureId adventureId, OwnerPlayerId owner, StageBackbone current,
            PremiseInvalidationProposal proposal, List<StageBackboneEntry> replacementSuffix) {
        if (artifacts == null) throw new IllegalStateException("stage artifact repository is required for replanning");
        Adventure adventure = adventures.findById(Objects.requireNonNull(adventureId, "adventure id must not be null"))
                .orElseThrow(() -> new IllegalStateException("adventure not found"));
        StoryRuntimeState state = adventure.storyRuntimeState();
        if (state == null) throw new IllegalStateException("story runtime state is not initialized");
        PremiseInvalidationResult decision = PremiseInvalidationPolicy.verify(state, current, proposal);
        StoryRuntimeState audited = state.recordPremiseInvalidation(decision);
        if (!decision.accepted()) {
            if (audited != state) {
                adventure.commitStoryRuntimeState(Objects.requireNonNull(owner, "owner must not be null"), adventure.version(), audited);
                adventures.save(adventure);
            }
            return new StoryRuntimeReplanResult(decision, current, audited);
        }
        StageBackbone replanned = FutureSuffixReplanningPolicy.replaceFrom(current, proposal.targetStageId(), replacementSuffix);
        artifacts.saveBackbone(replanned, current.revision());
        adventure.commitStoryRuntimeState(Objects.requireNonNull(owner, "owner must not be null"), adventure.version(), audited);
        adventures.save(adventure);
        return new StoryRuntimeReplanResult(decision, replanned, audited);
    }
}
