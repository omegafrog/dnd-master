package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.application.scenario.preparation.StageArtifactPreparationApplicationService;
import com.dndmaster.adventure.application.saved.AdventureRepository;
import com.dndmaster.adventure.domain.adventure.Adventure;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import com.dndmaster.adventure.domain.scenario.DetailedStage;
import com.dndmaster.adventure.domain.scenario.StageBackbone;
import java.util.Objects;

/**
 * Coordinates next-stage preparation before the single canonical runtime transition write.
 * A preparation failure therefore cannot leave the current stage half-closed.
 */
public final class StoryStageTransitionApplicationService {
    private final AdventureRepository adventures;
    private final StageArtifactPreparationApplicationService preparation;
    private final StoryRuntimeApplicationService runtime;

    public StoryStageTransitionApplicationService(AdventureRepository adventures,
            StageArtifactPreparationApplicationService preparation, StoryRuntimeApplicationService runtime) {
        this.adventures = Objects.requireNonNull(adventures, "adventure repository must not be null");
        this.preparation = Objects.requireNonNull(preparation, "stage preparation must not be null");
        this.runtime = Objects.requireNonNull(runtime, "story runtime service must not be null");
    }

    public Adventure transition(AdventureId adventureId, OwnerPlayerId owner, DetailedStage current,
            StageBackbone backbone) {
        Objects.requireNonNull(current, "current detailed stage must not be null");
        Objects.requireNonNull(backbone, "stage backbone must not be null");
        Adventure adventure = adventures.findById(Objects.requireNonNull(adventureId, "adventure id must not be null"))
                .orElseThrow(() -> new IllegalStateException("adventure not found"));
        if (adventure.storyRuntimeState() == null) throw new IllegalStateException("story runtime state is not initialized");
        DetailedStage next = preparation.prepareNext(current.scenarioPackageId(), backbone, current.stageId());
        return runtime.transition(adventureId, owner, current, next, backbone);
    }
}
