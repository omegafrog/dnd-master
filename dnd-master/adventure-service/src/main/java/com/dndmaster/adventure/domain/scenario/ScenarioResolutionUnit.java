package com.dndmaster.adventure.domain.scenario;

import java.util.List;
import java.util.Objects;

public record ScenarioResolutionUnit(
        ResolutionKind kind,
        String abilityOrSkill,
        Integer dc,
        String diceExpression,
        ResolutionVisibility visibility,
        String sourceQuote,
        List<ScenarioSourceReference> sourceRefs,
        String provenance,
        ResolutionStatus status,
        List<String> validationMessages) {
    public ScenarioResolutionUnit {
        visibility = Objects.requireNonNull(visibility, "visibility must not be null");
        sourceQuote = Objects.requireNonNull(sourceQuote, "source quote must not be null");
        sourceRefs = List.copyOf(Objects.requireNonNull(sourceRefs, "source refs must not be null"));
        provenance = Objects.requireNonNull(provenance, "provenance must not be null");
        status = Objects.requireNonNull(status, "resolution status must not be null");
        validationMessages = List.copyOf(Objects.requireNonNull(validationMessages, "validation messages must not be null"));
        if (sourceRefs.isEmpty() && status != ResolutionStatus.INVALID) {
            throw new IllegalArgumentException("resolution unit must have a source reference");
        }
    }
}
