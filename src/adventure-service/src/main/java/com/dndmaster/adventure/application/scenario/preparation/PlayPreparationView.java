package com.dndmaster.adventure.application.scenario.preparation;

import java.util.List;
import java.util.UUID;
import com.dndmaster.adventure.domain.scenario.StructuredSpellDefinition;
import com.dndmaster.adventure.domain.scenario.SpellDefinitionEditHistory;

public record PlayPreparationView(
        UUID scenarioPackageId,
        UUID bundleId,
        long bundleRevision,
        PlayPreparationStatus status,
        List<String> blockers,
        CharacterCreationBlueprintView characterCreationBlueprint,
        CharacterLimitView characterLimit,
        List<StructuredSpellDefinition> spellDefinitions,
        List<StructuredSpellDefinition> sourceSpellDefinitions,
        long spellDefinitionRevision,
        List<SpellDefinitionEditHistory> spellDefinitionHistory) {
    public PlayPreparationView {
        blockers = List.copyOf(blockers);
        spellDefinitions = List.copyOf(spellDefinitions);
        sourceSpellDefinitions = List.copyOf(sourceSpellDefinitions);
        spellDefinitionHistory = List.copyOf(spellDefinitionHistory);
    }

    public PlayPreparationView(UUID scenarioPackageId, UUID bundleId, long bundleRevision, PlayPreparationStatus status,
                               List<String> blockers, CharacterCreationBlueprintView characterCreationBlueprint,
                               CharacterLimitView characterLimit) {
        this(scenarioPackageId, bundleId, bundleRevision, status, blockers, characterCreationBlueprint,
                characterLimit, List.of(), List.of(), 0, List.of());
    }

    public PlayPreparationView(UUID scenarioPackageId, UUID bundleId, long bundleRevision, PlayPreparationStatus status,
                               List<String> blockers, CharacterCreationBlueprintView characterCreationBlueprint) {
        this(scenarioPackageId, bundleId, bundleRevision, status, blockers, characterCreationBlueprint,
                CharacterLimitView.defaultLimit(), List.of(), List.of(), 0, List.of());
    }

    public PlayPreparationView(UUID scenarioPackageId, UUID bundleId, long bundleRevision, PlayPreparationStatus status,
            List<String> blockers, CharacterCreationBlueprintView characterCreationBlueprint,
            CharacterLimitView characterLimit, List<StructuredSpellDefinition> spellDefinitions) {
        this(scenarioPackageId, bundleId, bundleRevision, status, blockers, characterCreationBlueprint,
                characterLimit, spellDefinitions, List.of(), 0, List.of());
    }
}
