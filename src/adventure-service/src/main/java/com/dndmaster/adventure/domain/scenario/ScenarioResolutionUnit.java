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
        ScenarioResolutionDetail detail,
        ResolutionStatus status,
        List<String> validationMessages) {
    public ScenarioResolutionUnit {
        visibility = Objects.requireNonNull(visibility, "visibility must not be null");
        sourceQuote = Objects.requireNonNull(sourceQuote, "source quote must not be null");
        sourceRefs = List.copyOf(Objects.requireNonNull(sourceRefs, "source refs must not be null"));
        provenance = Objects.requireNonNull(provenance, "provenance must not be null");
        detail = detail == null ? ScenarioResolutionDetail.empty() : detail;
        status = Objects.requireNonNull(status, "resolution status must not be null");
        validationMessages = List.copyOf(Objects.requireNonNull(validationMessages, "validation messages must not be null"));
        if (sourceRefs.isEmpty() && status != ResolutionStatus.INVALID) {
            throw new IllegalArgumentException("resolution unit must have a source reference");
        }
    }

    public List<String> runtimeCapabilities() {
        java.util.LinkedHashSet<String> capabilities = new java.util.LinkedHashSet<>();
        if (kind == null) return List.of();
        switch (kind) {
            case SKILL_ABILITY_CHECK, SAVING_THROW, PASSIVE_THRESHOLD, ATTACK_ROLL, OPPOSED_CHECK ->
                    capabilities.add("ATTACK_OR_SAVE");
            case DAMAGE_ROLL -> capabilities.add("DAMAGE");
            case HEALING_ROLL -> capabilities.add("HEALING");
            case INITIATIVE_ROLL -> capabilities.add("INITIATIVE");
            case RECHARGE_ROLL -> capabilities.add("RECHARGE");
            case RANDOM_TABLE -> capabilities.add("RANDOM_TABLE");
            case SPECIAL_ROLL -> capabilities.add("MANUAL_SPECIAL_ROLL");
            case DICE_ROLL -> capabilities.add("GENERIC_DICE");
        }
        for (ScenarioResolutionDetail.Step step : detail.steps()) {
            if (step.kind() == ResolutionKind.DAMAGE_ROLL) capabilities.add("DAMAGE");
            if (step.kind() == ResolutionKind.HEALING_ROLL) capabilities.add("HEALING");
            if (step.kind() == ResolutionKind.SAVING_THROW
                    || step.kind() == ResolutionKind.SKILL_ABILITY_CHECK
                    || step.kind() == ResolutionKind.ATTACK_ROLL
                    || step.kind() == ResolutionKind.OPPOSED_CHECK) {
                capabilities.add("ATTACK_OR_SAVE");
            }
        }
        return List.copyOf(capabilities);
    }
}
