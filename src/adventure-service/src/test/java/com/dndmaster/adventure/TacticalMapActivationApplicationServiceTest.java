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

    @Test
    void preparesPlanSelectedDefinitionEvenWithoutLegacyStoryBinding() {
        var mapId = UUID.randomUUID();
        var documentId = new KnowledgeDocumentId(UUID.randomUUID());
        var map = new MapDefinition(mapId, "brewery", "page 1 image 1", new MapDefinition.MapGrid(0, 0, 1, 0, "5ft"),
                List.of(), List.of(), List.of(), new MapSourceReference(documentId, 1, "asset:page 1 image 1"), .9, MapSafetyStatus.SAFE);
        var packageVersion = ScenarioPackage.publishWithMaps(ScenarioBundleId.generate(), 1, "plan-map", List.of(), List.of(),
                new ScenarioCompilationReport(ResolutionStatus.COMPLETE, List.of()),
                com.dndmaster.adventure.domain.scenario.CharacterLimit.defaultLimit(), null, List.of(map), List.of());
        var preparedId = UUID.randomUUID();
        var seenRuleSet = new UUID[1];
        var seenSpawn = new int[2];
        var service = new TacticalMapActivationApplicationService(new PackageRepository(packageVersion),
                new TacticalMapPreparationPort() {
                    public UUID prepare(UUID adventureId, UUID ownerId, MapDefinition definition) { return preparedId; }
                    public UUID prepare(UUID adventureId, UUID ownerId, UUID ruleSetId, MapDefinition definition) { seenRuleSet[0] = ruleSetId; return preparedId; }
                    public UUID prepare(UUID adventureId, UUID ownerId, UUID ruleSetId, MapDefinition definition, int x, int y) { seenRuleSet[0] = ruleSetId; seenSpawn[0] = x; seenSpawn[1] = y; return preparedId; }
                });

        var ruleSetId = UUID.randomUUID();
        var result = service.activateDefinition(packageVersion.packageId(), UUID.randomUUID(), UUID.randomUUID(), ruleSetId, mapId, 10, 13);

        assertEquals(Optional.of(preparedId), result.combatMapId());
        assertEquals(ruleSetId, seenRuleSet[0]);
        assertEquals(10, seenSpawn[0]);
        assertEquals(13, seenSpawn[1]);
    }

    private record PackageRepository(ScenarioPackage value) implements ScenarioPackageRepository {
        public Optional<ScenarioPackage> findByInputFingerprint(String ignored) { return Optional.empty(); }
        public Optional<ScenarioPackage> findById(UUID id) { return id.equals(value.packageId()) ? Optional.of(value) : Optional.empty(); }
        public void save(ScenarioPackage ignored) {}
    }
}
