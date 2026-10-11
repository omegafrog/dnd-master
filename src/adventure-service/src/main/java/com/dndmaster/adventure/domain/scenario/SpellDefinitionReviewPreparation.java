package com.dndmaster.adventure.domain.scenario;

import java.util.List;
import java.util.UUID;

/** User-owned review state for one adventure preparation; the ScenarioPackage remains immutable. */
public record SpellDefinitionReviewPreparation(UUID preparationId, UUID packageId, UUID ownerId,
        long revision, boolean locked, List<StructuredSpellDefinition> definitions) {
    public SpellDefinitionReviewPreparation {
        if (preparationId == null || packageId == null || ownerId == null || revision < 0 || definitions == null) {
            throw new IllegalArgumentException("invalid spell review preparation structure");
        }
        definitions = List.copyOf(definitions);
    }
}
