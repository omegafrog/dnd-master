package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.scenario.DetailedStage;
import com.dndmaster.adventure.domain.scenario.MapDefinition;
import com.dndmaster.adventure.domain.scenario.ScenarioPackage;
import com.dndmaster.adventure.domain.scenario.SituationDefinition;
import com.dndmaster.adventure.application.combat.CombatMapViewPort;
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
            addTypeFacts(facts, type);
            for (String cell : feature.authoritativeCells()) facts.add(cell.trim());
        }
        return facts.stream().filter(value -> value != null && !value.isBlank()).distinct().toList();
    }

    static List<String> forRuntimeFeatures(List<CombatMapViewPort.SpatialFeature> features) {
        if (features == null) return List.of();
        List<String> facts = new ArrayList<>();
        for (CombatMapViewPort.SpatialFeature feature : features) {
            if (feature == null || feature.type() == null || !"HIDDEN".equalsIgnoreCase(feature.visibility())
                    || "MAGICAL_AREA_EFFECT".equalsIgnoreCase(feature.type())) continue;
            addTypeFacts(facts, feature.type().trim().toUpperCase(Locale.ROOT));
            feature.cells().forEach(cell -> facts.add(cell.x() + "," + cell.y()));
        }
        return facts.stream().filter(value -> value != null && !value.isBlank()).distinct().toList();
    }

    private static void addTypeFacts(List<String> facts, String type) {
        facts.add(type);
        facts.add(type.toLowerCase(Locale.ROOT));
        switch (type) {
            case "TRAP" -> facts.addAll(List.of("함정", "압력판", "pressure plate"));
            case "SECRET_DOOR" -> facts.addAll(List.of("secret door", "비밀 문", "숨겨진 문"));
            case "HAZARD_AREA" -> facts.addAll(List.of("hazard area", "위험 구역"));
            case "INTERACTIVE_OBJECT" -> facts.addAll(List.of("interactive object", "상호작용 물체"));
            default -> { }
        }
    }
}
