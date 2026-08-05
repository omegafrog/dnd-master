package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndmaster.adventure.application.runtime.TacticalMapActivationPolicy;
import com.dndmaster.adventure.domain.scenario.StoryMapBinding;
import com.dndmaster.adventure.domain.scenario.MapDefinition;
import com.dndmaster.adventure.domain.scenario.MapSafetyStatus;
import com.dndmaster.adventure.domain.scenario.MapSourceReference;
import com.dndmaster.adventure.domain.scenario.ScenarioCompilationReport;
import com.dndmaster.adventure.domain.scenario.ScenarioPackage;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleId;
import com.dndmaster.adventure.domain.scenario.ResolutionStatus;
import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TacticalMapActivationPolicyTest {
    @Test void fixedTurnBindingsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new StoryMapBinding("stage", "crypt", "turn 3", UUID.randomUUID()));
    }

    @Test void unsafeMapUsesTextFallback() {
        var mapId = UUID.randomUUID();
        var documentId = new KnowledgeDocumentId(UUID.randomUUID());
        var map = new MapDefinition(mapId, "map", "image", new MapDefinition.MapGrid(0, 0, 1, 0, "5ft"),
                List.of(), List.of(), List.of(), new MapSourceReference(documentId, 1, "asset:map"), .9, MapSafetyStatus.UNSAFE);
        var packageVersion = ScenarioPackage.publishWithMaps(ScenarioBundleId.generate(), 1, "fingerprint", List.of(), List.of(),
                new ScenarioCompilationReport(ResolutionStatus.PARTIAL, List.of()),
                com.dndmaster.adventure.domain.scenario.CharacterLimit.defaultLimit(), null, List.of(map),
                List.of(new StoryMapBinding("stage", "crypt", "entered", mapId)));
        var activation = new TacticalMapActivationPolicy().decide(packageVersion, "stage", "crypt", "entered");
        assertTrue(activation.map().isEmpty());
        assertTrue(activation.textFallback());
    }
}
