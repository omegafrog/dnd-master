package com.dndmaster.adventure.domain.ruleset;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public record SelectedRulebooks(List<RegisteredRulebookReference> values) {
    public SelectedRulebooks(Collection<RegisteredRulebookReference> values) {
        this(copyAndValidate(values));
    }

    public SelectedRulebooks {
        values = copyAndValidate(values);
    }

    public boolean contains(RulebookId rulebookId) {
        return values.stream().anyMatch(reference -> reference.rulebookId().equals(rulebookId));
    }

    private static List<RegisteredRulebookReference> copyAndValidate(
            Collection<RegisteredRulebookReference> references) {
        Objects.requireNonNull(references, "selected rulebooks must not be null");
        List<RegisteredRulebookReference> copy = List.copyOf(references);
        if (copy.isEmpty()) {
            throw new IllegalArgumentException("at least one rulebook must be selected");
        }
        if (copy.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("selected rulebooks must not contain null");
        }
        var ids = new HashSet<RulebookId>();
        if (copy.stream().anyMatch(reference -> !ids.add(reference.rulebookId()))) {
            throw new IllegalArgumentException("selected rulebooks must have unique ids");
        }
        return copy;
    }
}
