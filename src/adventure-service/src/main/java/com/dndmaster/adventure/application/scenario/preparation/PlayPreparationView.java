package com.dndmaster.adventure.application.scenario.preparation;

import java.util.List;
import java.util.UUID;
import com.dndmaster.adventure.domain.scenario.StructuredSpellDefinition;
import com.dndmaster.adventure.domain.scenario.SpellDefinitionEditHistory;

public record PlayPreparationView(
        UUID preparationId,
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
        List<SpellDefinitionEditHistory> spellDefinitionHistory,
        List<PreparationSourceView> packageSources) {
    public PlayPreparationView {
        blockers = List.copyOf(blockers);
        spellDefinitions = List.copyOf(spellDefinitions);
        sourceSpellDefinitions = List.copyOf(sourceSpellDefinitions);
        spellDefinitionHistory = List.copyOf(spellDefinitionHistory);
        packageSources = List.copyOf(packageSources);
    }

    public PlayPreparationView(UUID preparationId, UUID scenarioPackageId, UUID bundleId, long bundleRevision,
            PlayPreparationStatus status, List<String> blockers, CharacterCreationBlueprintView blueprint,
            CharacterLimitView characterLimit, List<StructuredSpellDefinition> definitions,
            List<StructuredSpellDefinition> sourceDefinitions, long revision, List<SpellDefinitionEditHistory> history) {
        this(preparationId, scenarioPackageId, bundleId, bundleRevision, status, blockers, blueprint, characterLimit,
                definitions, sourceDefinitions, revision, history, List.of());
    }

    public PlayPreparationView(UUID scenarioPackageId, UUID bundleId, long bundleRevision, PlayPreparationStatus status,
            List<String> blockers, CharacterCreationBlueprintView characterCreationBlueprint, CharacterLimitView characterLimit,
            List<StructuredSpellDefinition> spellDefinitions, List<StructuredSpellDefinition> sourceSpellDefinitions,
            long spellDefinitionRevision, List<SpellDefinitionEditHistory> spellDefinitionHistory) {
        this(null, scenarioPackageId, bundleId, bundleRevision, status, blockers, characterCreationBlueprint,
                characterLimit, spellDefinitions, sourceSpellDefinitions, spellDefinitionRevision, spellDefinitionHistory);
    }

    public PlayPreparationView(UUID scenarioPackageId, UUID bundleId, long bundleRevision, PlayPreparationStatus status,
                               List<String> blockers, CharacterCreationBlueprintView characterCreationBlueprint,
                               CharacterLimitView characterLimit) {
        this(null, scenarioPackageId, bundleId, bundleRevision, status, blockers, characterCreationBlueprint,
                characterLimit, List.of(), List.of(), 0, List.of());
    }

    public PlayPreparationView(UUID scenarioPackageId, UUID bundleId, long bundleRevision, PlayPreparationStatus status,
                               List<String> blockers, CharacterCreationBlueprintView characterCreationBlueprint) {
        this(null, scenarioPackageId, bundleId, bundleRevision, status, blockers, characterCreationBlueprint,
                CharacterLimitView.defaultLimit(), List.of(), List.of(), 0, List.of());
    }

    public PlayPreparationView(UUID scenarioPackageId, UUID bundleId, long bundleRevision, PlayPreparationStatus status,
            List<String> blockers, CharacterCreationBlueprintView characterCreationBlueprint,
            CharacterLimitView characterLimit, List<StructuredSpellDefinition> spellDefinitions) {
        this(null, scenarioPackageId, bundleId, bundleRevision, status, blockers, characterCreationBlueprint,
                characterLimit, spellDefinitions, List.of(), 0, List.of());
    }
}
