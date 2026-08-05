package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.adventure.application.runtime.TacticalMapActivationApplicationService;
import com.dndmaster.adventure.application.runtime.TacticalMapPreparationPort;
import com.dndmaster.adventure.application.scenario.compilation.ScenarioPackageRepository;
import com.dndmaster.adventure.domain.scenario.ScenarioPackage;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleId;
import com.dndmaster.adventure.domain.scenario.MapDefinition;
import com.dndmaster.adventure.domain.scenario.MapSafetyStatus;
import com.dndmaster.adventure.domain.scenario.MapSourceReference;
import com.dndmaster.adventure.domain.scenario.StoryMapBinding;
import com.dndmaster.adventure.domain.scenario.ScenarioCompilationReport;
import com.dndmaster.adventure.domain.scenario.ResolutionStatus;
import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TacticalMapActivationApplicationServiceTest {
    @Test
    void preparesSelectedMapAndReturnsCombatMapIdentity() {
        var mapId = UUID.randomUUID();
        var documentId = new KnowledgeDocumentId(UUID.randomUUID());
        var map = new MapDefinition(mapId, "crypt", "image", new MapDefinition.MapGrid(0, 0, 1, 0, "5ft"),
                List.of(), List.of(), List.of(), new MapSourceReference(documentId, 1, "asset:crypt"), .95, MapSafetyStatus.SAFE);
        var packageVersion = ScenarioPackage.publishWithMaps(ScenarioBundleId.generate(), 1, "fingerprint", List.of(), List.of(),
                new ScenarioCompilationReport(ResolutionStatus.COMPLETE, List.of()),
                com.dndmaster.adventure.domain.scenario.CharacterLimit.defaultLimit(), null, List.of(map),
                List.of(new StoryMapBinding("stage", "crypt", "entered", mapId)));
        var preparedId = UUID.randomUUID();
        var service = new TacticalMapActivationApplicationService(new PackageRepository(packageVersion),
                (adventureId, ownerId, definition) -> preparedId);

        var result = service.activate(packageVersion.packageId(), UUID.randomUUID(), UUID.randomUUID(), "stage", "crypt", "entered");

        assertEquals(Optional.of(preparedId), result.combatMapId());
        assertTrue(!result.textFallback());
    }

    private record PackageRepository(ScenarioPackage value) implements ScenarioPackageRepository {
        public Optional<ScenarioPackage> findByInputFingerprint(String ignored) { return Optional.empty(); }
        public Optional<ScenarioPackage> findById(UUID id) { return id.equals(value.packageId()) ? Optional.of(value) : Optional.empty(); }
        public void save(ScenarioPackage ignored) {}
    }
}
