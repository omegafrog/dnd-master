package com.dndmaster.adventure.application.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.dndmaster.adventure.application.runtime.DefaultNarrationSafetyPolicy;
import com.dndmaster.adventure.application.runtime.EvidencePack;
import com.dndmaster.adventure.application.runtime.NarrationSafetyRequest;
import com.dndmaster.adventure.domain.adventure.AdventureContext;
import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import com.dndmaster.adventure.application.combat.CombatMapViewPort;
import com.dndmaster.adventure.domain.scenario.MapDefinition;
import com.dndmaster.adventure.domain.scenario.MapSafetyStatus;
import com.dndmaster.adventure.domain.scenario.MapSourceReference;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HiddenMapNarrationFactsTest {
    private final DefaultNarrationSafetyPolicy policy = new DefaultNarrationSafetyPolicy();

    @Test
    void mapNarrationAllowsOrdinaryDetailsAndRejectsHiddenFeatureTypeAndCoordinate() {
        MapDefinition map = mapWithHiddenTrap();
        List<String> hiddenFacts = HiddenMapNarrationFacts.forMap(map);
        var context = new AdventureContext("stone room", null, "enter", null);
        var evidence = new EvidencePack(List.of(), List.of(), List.of());

        assertThat(policy.assess(new NarrationSafetyRequest("A stone room contains a wooden table and a cracked wall.",
                evidence, context, "enter", hiddenFacts)).approved()).isTrue();
        assertThat(policy.assess(new NarrationSafetyRequest("A pressure plate is concealed below the dust.",
                evidence, context, "enter", hiddenFacts)).approved()).isFalse();
        assertThat(policy.assess(new NarrationSafetyRequest("A mechanism lies at 4,3.",
                evidence, context, "enter", hiddenFacts)).approved()).isFalse();
        assertThat(policy.assess(new NarrationSafetyRequest("A mechanism lies at (4, 3).",
                evidence, context, "enter", hiddenFacts)).approved()).isFalse();
    }

    @Test
    void generatedHiddenMapPlacementIsCheckedButDiscoveredPlacementIsNot() {
        List<String> hiddenFacts = HiddenMapNarrationFacts.forRuntimeFeatures(List.of(
                new CombatMapViewPort.SpatialFeature(UUID.randomUUID(), "TRAP", List.of(
                        new CombatMapViewPort.Position(8, 5)), "HIDDEN", "HIDDEN", false),
                new CombatMapViewPort.SpatialFeature(UUID.randomUUID(), "SECRET_DOOR", List.of(
                        new CombatMapViewPort.Position(2, 1)), "DISCOVERED", "DISCOVERED", false)));
        var context = new AdventureContext("stone room", null, "enter", null);
        var evidence = new EvidencePack(List.of(), List.of(), List.of());

        assertThat(hiddenFacts).contains("8,5", "pressure plate").doesNotContain("2,1");
        assertThat(policy.assess(new NarrationSafetyRequest("A pressure plate is at (8, 5).",
                evidence, context, "enter", hiddenFacts)).approved()).isFalse();
        assertThat(policy.assess(new NarrationSafetyRequest("A stone tile is at (2, 1).",
                evidence, context, "enter", hiddenFacts)).approved()).isTrue();
    }

    private static MapDefinition mapWithHiddenTrap() {
        var requirement = new MapDefinition.SpatialFeatureRequirement(UUID.randomUUID(), "TRAP", true,
                List.of("document:source:1:map:1"), List.of("4,3"), "", null, "", List.of());
        return new MapDefinition(UUID.randomUUID(), "map", "map-image", new MapDefinition.MapGrid(0, 0, 1, 0, "feet"),
                List.of(), List.of(), List.of(), new MapSourceReference(new KnowledgeDocumentId(UUID.randomUUID()), 1, "map:1"),
                1, MapSafetyStatus.SAFE, List.of(requirement));
    }
}
