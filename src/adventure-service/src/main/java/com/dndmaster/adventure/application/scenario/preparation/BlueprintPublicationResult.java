package com.dndmaster.adventure.application.scenario.preparation;

public record BlueprintPublicationResult(
        long publishedRevision,
        CharacterCreationBlueprintView.AppliedSettingsSummaryView appliedSettingsSummary) {}
