package com.dndmaster.adventure.application.scenario.preparation;

import java.util.List;
import java.util.UUID;
import com.dndmaster.adventure.domain.scenario.StructuredSpellDefinition;

public record PlayPreparationView(
        UUID scenarioPackageId,
        UUID bundleId,
        long bundleRevision,
        PlayPreparationStatus status,
        List<String> blockers,
        CharacterCreationBlueprintView characterCreationBlueprint,
        CharacterLimitView characterLimit,
        List<StructuredSpellDefinition> spellDefinitions) {
    public PlayPreparationView {
        blockers = List.copyOf(blockers);
        spellDefinitions = List.copyOf(spellDefinitions);
    }

    public PlayPreparationView(UUID scenarioPackageId, UUID bundleId, long bundleRevision, PlayPreparationStatus status,
                               List<String> blockers, CharacterCreationBlueprintView characterCreationBlueprint,
                               CharacterLimitView characterLimit) {
        this(scenarioPackageId, bundleId, bundleRevision, status, blockers, characterCreationBlueprint,
                characterLimit, List.of());
    }

    public PlayPreparationView(UUID scenarioPackageId, UUID bundleId, long bundleRevision, PlayPreparationStatus status,
                               List<String> blockers, CharacterCreationBlueprintView characterCreationBlueprint) {
        this(scenarioPackageId, bundleId, bundleRevision, status, blockers, characterCreationBlueprint,
                CharacterLimitView.defaultLimit(), List.of());
    }
}
