package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.scenario.DetailedStage;
import com.dndmaster.adventure.domain.scenario.MapDefinition;
import com.dndmaster.adventure.domain.scenario.ScenarioPackage;
import com.dndmaster.adventure.domain.scenario.SituationDefinition;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Source-derived map terms withheld from player narration until the mapped situation reveals them. */
final class HiddenMapNarrationFacts {
    private HiddenMapNarrationFacts() {}

    static List<String> forSituation(ScenarioPackage scenarioPackage, DetailedStage stage, String situationId) {
        if (scenarioPackage == null || stage == null || situationId == null || situationId.isBlank()) return List.of();
        SituationDefinition situation = stage.situations().stream()
                .filter(candidate -> candidate.situationId().equals(situationId))
                .findFirst().orElse(null);
        if (situation == null || !Boolean.TRUE.equals(situation.mapNeeded()) || situation.mapDefinitionId() == null) return List.of();
        MapDefinition map = scenarioPackage.mapDefinitions().stream()
                .filter(candidate -> candidate.id().equals(situation.mapDefinitionId()))
                .findFirst().orElse(null);
        return forMap(map);
    }

    static List<String> forMap(MapDefinition map) {
        if (map == null) return List.of();
        List<String> facts = new ArrayList<>();
        for (MapDefinition.SpatialFeatureRequirement feature : map.spatialFeatures()) {
            String type = feature.type().trim().toUpperCase(Locale.ROOT);
            if ("MAGICAL_AREA_EFFECT".equals(type)) continue;
            facts.add(type);
            facts.add(type.toLowerCase(Locale.ROOT));
            if ("TRAP".equals(type)) facts.addAll(List.of("trap", "함정", "압력판", "pressure plate"));
            for (String cell : feature.authoritativeCells()) facts.add(cell.trim());
        }
        return facts.stream().filter(value -> value != null && !value.isBlank()).distinct().toList();
    }
}
