package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.scenario.DetailedStage;
import com.dndmaster.adventure.domain.scenario.CombatScenarioDefinition;
import com.dndmaster.adventure.domain.scenario.MapDefinition;
import com.dndmaster.adventure.domain.scenario.ScenarioModel;
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

    static List<String> forHiddenEnemyTokens(String gmMapJson, ScenarioModel scenarioModel, String activeCombatScenarioId) {
        if (gmMapJson == null || gmMapJson.isBlank()) return List.of();
        try {
            var root = new com.fasterxml.jackson.databind.ObjectMapper().readTree(gmMapJson);
            var tokens = root.path("tokens");
            if (!tokens.isArray()) throw new IllegalStateException("GM map view has no token list");
            List<CombatScenarioDefinition> scenarioEnemies = scenarioModel == null ? List.of()
                    : scenarioModel.combatScenarios().stream()
                            .filter(enemy -> activeCombatScenarioId == null || enemy.scenarioId().equals(activeCombatScenarioId))
                            .toList();
            List<String> facts = new ArrayList<>();
            for (var token : tokens) {
                String type = token.path("type").asText("");
                if (!("ENEMY".equalsIgnoreCase(type) || "BOSS".equalsIgnoreCase(type))
                        || !"HIDDEN".equalsIgnoreCase(token.path("discovery").asText(""))) continue;
                int x = token.path("x").asInt();
                int y = token.path("y").asInt();
                String cell = x + "," + y;
                facts.addAll(List.of(type, type.toLowerCase(Locale.ROOT), "적", "위협", "enemy", "hostile", cell));
                for (String nameField : List.of("name", "displayName", "label", "enemyName")) {
                    String name = token.path(nameField).asText("").trim();
                    if (!name.isBlank()) facts.add(name);
                }
                for (CombatScenarioDefinition enemy : scenarioEnemies) {
                    facts.add(enemy.displayName());
                    facts.add(enemy.enemyKey());
                }
            }
            return facts.stream().filter(value -> value != null && !value.isBlank()).distinct().toList();
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("GM map view could not be parsed for hidden-enemy narration checks", failure);
        }
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
