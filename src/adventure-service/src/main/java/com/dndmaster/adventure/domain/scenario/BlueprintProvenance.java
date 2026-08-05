package com.dndmaster.adventure.domain.scenario;

import java.util.List;

public record BlueprintProvenance(long gameSystemDefinitionVersion, long sourceRevision, List<String> sourceTypes) {
    public BlueprintProvenance {
        if (gameSystemDefinitionVersion < 0 || sourceRevision < 0) throw new IllegalArgumentException("blueprint provenance versions must not be negative");
        sourceTypes = List.copyOf(sourceTypes == null ? List.of() : sourceTypes);
    }
    public static BlueprintProvenance empty() { return new BlueprintProvenance(0, 0, List.of()); }
}
