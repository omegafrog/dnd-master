package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndmaster.adventure.application.scenario.compilation.ScenarioPackageRepository;
import com.dndmaster.adventure.application.scenario.compilation.ResolutionExtractionPort;
import com.dndmaster.adventure.application.scenario.compilation.ScenarioPackageCompilationService;
import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import com.dndmaster.adventure.domain.scenario.KnowledgeDocumentStatus;
import com.dndmaster.adventure.domain.scenario.OwnerPlayerId;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleDocumentRole;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleDocumentSelection;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleId;
import com.dndmaster.adventure.domain.scenario.ScenarioSourceBundle;
import com.dndmaster.adventure.domain.scenario.ScenarioSourceBundleRevision;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BundleMapCompilationTest {
    @Test
    void compilesMapAssetWithLockedSourceIdentity() {
        var documentId = new KnowledgeDocumentId(UUID.randomUUID());
        var bundle = ScenarioSourceBundle.create(new ScenarioBundleId(UUID.randomUUID()),
                new OwnerPlayerId(UUID.randomUUID()), new ScenarioSourceBundleRevision(3, List.of(
                        new ScenarioBundleDocumentSelection(documentId, ScenarioBundleDocumentRole.MAP,
                                com.dndmaster.adventure.application.knowledge.KnowledgeDocumentStatus.INDEXED, "crypt.png", "IMAGE", 7))));

        var result = new ScenarioPackageCompilationService(new PackageRepository()).compile(bundle, List.of(),
                List.of(new ResolutionExtractionPort.SourceExcerpt(documentId, 7, "asset:map-1",
                        "MAP asset=map-1 image=https://cdn/maps/crypt.png grid=1.0 origin=0,0 rotation=0 distance=5ft confidence=0.98 safety=SAFE")));

        assertEquals(1, result.mapDefinitions().size());
        var map = result.mapDefinitions().getFirst();
        assertEquals(documentId, map.source().knowledgeDocumentId());
        assertEquals(7, map.source().extractionVersion());
        assertEquals("map-1", map.assetId());
        assertEquals("SAFE", map.safetyStatus().name());
        assertFalse(map.source().locator().isBlank());
        assertEquals(map, result.initialMapDefinition("opening").orElseThrow());
    }

    @Test
    void rejectsMapSourceFromUnlockedExtractionVersion() {
        var documentId = new KnowledgeDocumentId(UUID.randomUUID());
        var bundle = ScenarioSourceBundle.create(new ScenarioBundleId(UUID.randomUUID()),
                new OwnerPlayerId(UUID.randomUUID()), new ScenarioSourceBundleRevision(1, List.of(
                        new ScenarioBundleDocumentSelection(documentId, ScenarioBundleDocumentRole.MAP,
                                com.dndmaster.adventure.application.knowledge.KnowledgeDocumentStatus.INDEXED, "map.png", "IMAGE", 4))));

        assertThrows(IllegalArgumentException.class, () ->
                new ScenarioPackageCompilationService(new PackageRepository()).compile(bundle, List.of(),
                        List.of(new ResolutionExtractionPort.SourceExcerpt(documentId, 5, "asset:map-1", "MAP asset=map-1"))));
    }

    @Test
    void preservesQuotedMapAssetLocatorsContainingSpaces() {
        var documentId = new KnowledgeDocumentId(UUID.randomUUID());
        var bundle = ScenarioSourceBundle.create(new ScenarioBundleId(UUID.randomUUID()),
                new OwnerPlayerId(UUID.randomUUID()), new ScenarioSourceBundleRevision(1, List.of(
                        new ScenarioBundleDocumentSelection(documentId, ScenarioBundleDocumentRole.MAP,
                                com.dndmaster.adventure.application.knowledge.KnowledgeDocumentStatus.INDEXED,
                                "map.pdf", "PDF", 2))));

        var result = new ScenarioPackageCompilationService(new PackageRepository()).compile(bundle, List.of(),
                List.of(new ResolutionExtractionPort.SourceExcerpt(documentId, 2, "asset:page 1 image 1",
                        "MAP asset=\"page 1 image 1\" image=\"page 1 image 1\" confidence=0.9 safety=SAFE")));

        assertEquals("page 1 image 1", result.mapDefinitions().getFirst().assetId());
        assertEquals("page 1 image 1", result.mapDefinitions().getFirst().assetLocator());
    }

    @Test
    void ignoresMapWordsInOrdinaryDocumentText() {
        var documentId = new KnowledgeDocumentId(UUID.randomUUID());
        var bundle = ScenarioSourceBundle.create(new ScenarioBundleId(UUID.randomUUID()),
                new OwnerPlayerId(UUID.randomUUID()), new ScenarioSourceBundleRevision(1, List.of(
                        new ScenarioBundleDocumentSelection(documentId, ScenarioBundleDocumentRole.MAP,
                                com.dndmaster.adventure.application.knowledge.KnowledgeDocumentStatus.INDEXED,
                                "map.pdf", "PDF", 2))));

        var result = new ScenarioPackageCompilationService(new PackageRepository()).compile(bundle, List.of(),
                List.of(new ResolutionExtractionPort.SourceExcerpt(documentId, 2, "document:page-1",
                        "A Most Potent Brew - Map")));

        assertEquals(0, result.mapDefinitions().size());
    }

    private static final class PackageRepository implements ScenarioPackageRepository {
        private final Map<String, com.dndmaster.adventure.domain.scenario.ScenarioPackage> packages = new HashMap<>();
        public Optional<com.dndmaster.adventure.domain.scenario.ScenarioPackage> findByInputFingerprint(String key) { return Optional.ofNullable(packages.get(key)); }
        public Optional<com.dndmaster.adventure.domain.scenario.ScenarioPackage> findById(UUID id) { return packages.values().stream().filter(p -> p.packageId().equals(id)).findFirst(); }
        public void save(com.dndmaster.adventure.domain.scenario.ScenarioPackage value) { packages.put(value.inputFingerprint(), value); }
    }
}
