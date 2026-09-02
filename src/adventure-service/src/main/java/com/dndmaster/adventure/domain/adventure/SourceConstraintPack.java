package com.dndmaster.adventure.domain.adventure;

import java.util.List;

/** Separate Storybook authority from Rulebook context while exposing a deterministic precedence view. */
public record SourceConstraintPack(List<SourceConstraint> storybookConstraints, List<SourceConstraint> rulebookConstraints) {
    public SourceConstraintPack {
        storybookConstraints = storybookConstraints == null ? List.of() : List.copyOf(storybookConstraints);
        rulebookConstraints = rulebookConstraints == null ? List.of() : List.copyOf(rulebookConstraints);
    }

    public List<SourceConstraint> effectiveConstraints() {
        return java.util.stream.Stream.concat(storybookConstraints.stream(), rulebookConstraints.stream()
                .filter(rule -> storybookConstraints.stream().noneMatch(story ->
                        story.fieldPath().equals(rule.fieldPath()) && !story.normalizedClaim().equals(rule.normalizedClaim()))))
                .toList();
    }
}
