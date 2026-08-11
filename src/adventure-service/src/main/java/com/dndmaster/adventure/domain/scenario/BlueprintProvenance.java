package com.dndmaster.adventure.domain.scenario;

import java.util.List;

public record BlueprintProvenance(long gameSystemDefinitionVersion, long sourceRevision, List<String> sourceTypes,
                                  String edition) {
    public BlueprintProvenance(long gameSystemDefinitionVersion, long sourceRevision, List<String> sourceTypes) {
        this(gameSystemDefinitionVersion, sourceRevision, sourceTypes, "DND_5E_2014");
    }
    public BlueprintProvenance {
        if (gameSystemDefinitionVersion < 0 || sourceRevision < 0) throw new IllegalArgumentException("blueprint provenance versions must not be negative");
        sourceTypes = List.copyOf(sourceTypes == null ? List.of() : sourceTypes);
        edition = "DND_5E".equalsIgnoreCase(edition) ? "DND_5E_2014" : edition;
        if (edition == null || edition.isBlank()) throw new IllegalArgumentException("blueprint edition must not be blank");
    }
    public static BlueprintProvenance empty() { return new BlueprintProvenance(0, 0, List.of(), "DND_5E_2014"); }
}
