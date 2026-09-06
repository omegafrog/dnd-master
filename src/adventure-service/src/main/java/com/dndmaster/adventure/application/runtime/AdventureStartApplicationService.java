package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.application.saved.AdventureRepository;
import com.dndmaster.adventure.application.scenario.compilation.ScenarioPackageRepository;
import com.dndmaster.adventure.domain.adventure.Adventure;
import com.dndmaster.adventure.domain.scenario.ScenarioPackage;
import java.util.Objects;

/** Starts a Scenario Model Runtime Adventure and owns the READY guard. */
public final class AdventureStartApplicationService {
    private final ScenarioPackageRepository packages;
    private final AdventureRepository adventures;
    private final com.dndmaster.adventure.application.scenario.preparation.StageArtifactPreparationApplicationService stagePreparation;

    public AdventureStartApplicationService(ScenarioPackageRepository packages, AdventureRepository adventures,
            com.dndmaster.adventure.application.scenario.preparation.StageArtifactPreparationApplicationService stagePreparation) {
        this.packages = Objects.requireNonNull(packages, "package repository must not be null");
        this.adventures = Objects.requireNonNull(adventures, "adventure repository must not be null");
        this.stagePreparation = Objects.requireNonNull(stagePreparation, "stage preparation must not be null");
    }

    public Adventure start(StartAdventureCommand command) {
        Objects.requireNonNull(command, "start command must not be null");
        ScenarioPackage scenarioPackage = packages.findById(command.scenarioPackageId())
                .orElseThrow(() -> new IllegalStateException("scenario package not found"));
        if (!scenarioPackage.isReady()) throw new IllegalStateException("scenario package is not READY");
        if (scenarioPackage.bundleRevision() != command.scenarioPackageRevision()) {
            throw new IllegalStateException("scenario package revision does not match start request");
        }

        Adventure adventure = adventures.findById(command.adventureId()).orElse(null);
        if (adventure != null && adventure.status() == com.dndmaster.adventure.domain.adventure.AdventureStatus.ACTIVE) return adventure;
        var prepared = stagePreparation.prepare(scenarioPackage.packageId());
        if (adventure == null) {
            adventure = Adventure.beginScenarioRuntime(command.adventureId(), command.sessionId(), command.ownerPlayerId(),
                    command.scenarioId(), command.ruleSetId(), command.scenarioPackageId(), command.scenarioPackageRevision(),
                    command.party(), command.initialContext());
            adventures.save(adventure);
        } else {
            if (!adventure.ownerPlayerId().equals(command.ownerPlayerId())) throw new SecurityException("adventure access denied");
            if (!command.scenarioPackageId().equals(adventure.lockedScenarioPackageId())) {
                throw new IllegalStateException("adventure is locked to another scenario package");
            }
            if (adventure.status() != com.dndmaster.adventure.domain.adventure.AdventureStatus.STARTING) {
                throw new IllegalStateException("adventure cannot resume from " + adventure.status());
            }
        }

        if (adventure.currentSituation() == null) {
            adventure.initializeScenarioRuntime(command.ownerPlayerId(),
                    com.dndmaster.adventure.domain.runtime.GameState.empty(),
                    com.dndmaster.adventure.domain.runtime.DisclosureState.empty(),
                    com.dndmaster.adventure.domain.runtime.CurrentSituation.initial(prepared.openingSituation().situationId()),
                    java.util.List.of(),
                    new com.dndmaster.adventure.domain.adventure.AdventureContext(
                            prepared.openingSituation().situationId(), null, null, null),
                    prepared.currentStage() == null ? null
                            : com.dndmaster.adventure.domain.runtime.story.StoryRuntimeState.start(prepared.currentStage()));
            adventures.save(adventure);
        }
        return adventure;
    }
}
