package com.dndmaster.adventure.domain.scenario;

import java.util.List;
import java.util.Objects;

public record SituationDefinition(String situationId, List<StageIntent> intents,
                                  List<String> revelationIds, List<String> pressureIds, List<String> funnelIds,
                                  List<String> funnelPredicateIds, List<String> importantConsequenceIds, boolean opening,
                                  boolean required, List<String> prerequisiteSituationIds,
                                  SituationProvenance provenance, java.util.UUID mapDefinitionId,
                                  List<ScenarioSourceReference> sourceRefs) {
    public SituationDefinition {
        if (situationId == null || situationId.isBlank()) throw new IllegalArgumentException("situation id is required");
        intents = List.copyOf(Objects.requireNonNull(intents, "situation intents must not be null"));
        revelationIds = List.copyOf(Objects.requireNonNull(revelationIds, "situation revelation ids must not be null"));
        pressureIds = List.copyOf(Objects.requireNonNull(pressureIds, "situation pressure ids must not be null"));
        funnelIds = List.copyOf(Objects.requireNonNull(funnelIds, "situation funnel ids must not be null"));
        funnelPredicateIds = List.copyOf(Objects.requireNonNull(funnelPredicateIds, "situation funnel predicate ids must not be null"));
        importantConsequenceIds = List.copyOf(Objects.requireNonNull(importantConsequenceIds, "situation consequence ids must not be null"));
        prerequisiteSituationIds = List.copyOf(Objects.requireNonNull(prerequisiteSituationIds, "situation prerequisites must not be null"));
        if (prerequisiteSituationIds.stream().anyMatch(value -> value == null || value.isBlank() || value.equals(situationId))) {
            throw new IllegalArgumentException("situation prerequisites must reference another situation");
        }
        if (prerequisiteSituationIds.stream().distinct().count() != prerequisiteSituationIds.size()) {
            throw new IllegalArgumentException("situation prerequisites must be unique");
        }
        provenance = Objects.requireNonNull(provenance, "situation provenance must not be null");
        sourceRefs = List.copyOf(Objects.requireNonNull(sourceRefs, "situation source refs must not be null"));
        if (intents.isEmpty()) throw new IllegalArgumentException("situation must have a stage intent");
        if (revelationIds.isEmpty() && pressureIds.isEmpty() && funnelIds.isEmpty() && funnelPredicateIds.isEmpty() && importantConsequenceIds.isEmpty()) {
            throw new IllegalArgumentException("situation must reference a stage intent target");
        }
    }

    public SituationDefinition(String situationId, List<StageIntent> intents, List<String> revelationIds,
            List<String> pressureIds, List<String> funnelIds, List<String> funnelPredicateIds,
            List<String> importantConsequenceIds, boolean opening, List<ScenarioSourceReference> sourceRefs) {
        this(situationId, intents, revelationIds, pressureIds, funnelIds, funnelPredicateIds,
                importantConsequenceIds, opening, opening, List.of(),
                sourceRefs == null || sourceRefs.isEmpty() ? SituationProvenance.GM_SUPPLEMENTED : SituationProvenance.SOURCE_EXTRACTED,
                null, sourceRefs);
    }

    public SituationDefinition(String situationId, List<StageIntent> intents, List<ScenarioSourceReference> sourceRefs) {
        this(situationId, intents, List.of(), List.of(), List.of(), List.of(), List.of(), false, false, List.of(),
                sourceRefs == null || sourceRefs.isEmpty() ? SituationProvenance.GM_SUPPLEMENTED : SituationProvenance.SOURCE_EXTRACTED,
                null, sourceRefs);
    }
}
