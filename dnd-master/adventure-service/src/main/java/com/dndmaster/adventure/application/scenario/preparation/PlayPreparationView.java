package com.dndmaster.adventure.application.scenario.preparation;

import java.util.List;
import java.util.UUID;

public record PlayPreparationView(
        UUID scenarioPackageId,
        UUID bundleId,
        long bundleRevision,
        PlayPreparationStatus status,
        List<String> blockers,
        CharacterCreationBlueprintView characterCreationBlueprint,
        CharacterLimitView characterLimit) {
    public PlayPreparationView {
        blockers = List.copyOf(blockers);
    }

    public PlayPreparationView(UUID scenarioPackageId, UUID bundleId, long bundleRevision, PlayPreparationStatus status,
                               List<String> blockers, CharacterCreationBlueprintView characterCreationBlueprint) {
        this(scenarioPackageId, bundleId, bundleRevision, status, blockers, characterCreationBlueprint, CharacterLimitView.defaultLimit());
    }
}
