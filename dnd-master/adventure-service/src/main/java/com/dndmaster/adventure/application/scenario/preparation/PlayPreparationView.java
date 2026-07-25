package com.dndmaster.adventure.application.scenario.preparation;

import java.util.List;
import java.util.UUID;

public record PlayPreparationView(
        UUID scenarioPackageId,
        UUID bundleId,
        long bundleRevision,
        PlayPreparationStatus status,
        List<String> blockers,
        CharacterCreationBlueprintView characterCreationBlueprint) {
    public PlayPreparationView {
        blockers = List.copyOf(blockers);
    }
}
