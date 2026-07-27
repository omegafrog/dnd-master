package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.adventure.application.knowledge.KnowledgeDocumentLookupPort;
import com.dndmaster.adventure.application.knowledge.KnowledgeDocumentStatus;
import com.dndmaster.adventure.application.scenario.AdventureScenarioRepository;
import com.dndmaster.adventure.application.scenario.BundleDocumentDraft;
import com.dndmaster.adventure.application.scenario.LegacyScenarioIngestionPort;
import com.dndmaster.adventure.application.scenario.LegacyScenarioMigrationApplicationService;
import com.dndmaster.adventure.application.scenario.LegacyScenarioMigrationStateRepository;
import com.dndmaster.adventure.application.scenario.ScenarioBundleApplicationService;
import com.dndmaster.adventure.application.scenario.ScenarioBundleRepository;
import com.dndmaster.adventure.application.scenario.ScenarioStoragePort;
import com.dndmaster.adventure.application.scenario.ScenarioUpload;
import com.dndmaster.adventure.application.scenario.compilation.ResolutionExtractionPort;
import com.dndmaster.adventure.application.scenario.compilation.ScenarioCompilationApplicationService;
import com.dndmaster.adventure.application.scenario.compilation.ScenarioCompilationProcessManager;
import com.dndmaster.adventure.application.scenario.compilation.ScenarioCompilationRepository;
import com.dndmaster.adventure.application.scenario.compilation.ScenarioPackageCompilationService;
import com.dndmaster.adventure.application.scenario.compilation.ScenarioPackageRepository;
import com.dndmaster.adventure.application.scenario.compilation.ScenarioSourceExcerptPort;
import com.dndmaster.adventure.application.scenario.compilation.WorkEnvelope;
import com.dndmaster.adventure.application.scenario.compilation.WorkQueuePort;
import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import com.dndmaster.adventure.domain.scenario.AdventureScenario;
import com.dndmaster.adventure.domain.scenario.OwnerPlayerId;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleDocumentRole;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleId;
import com.dndmaster.adventure.domain.scenario.ScenarioId;
import com.dndmaster.adventure.domain.scenario.ScenarioPackage;
import com.dndmaster.adventure.domain.scenario.ScenarioSource;
import com.dndmaster.adventure.domain.scenario.ScenarioSourceBundle;
import com.dndmaster.adventure.domain.scenario.ScenarioSourceBundleRevision;
import com.dndmaster.adventure.domain.scenario.ScenarioSourceReference;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LegacyScenarioMigrationApplicationServiceTest {
    @Test
    void migratesRecoverableLegacySourceIntoBundleAndPackage() {
        OwnerPlayerId owner = new OwnerPlayerId(UUID.randomUUID());
        ScenarioScenarioFixture fixture = fixture(owner, "legacy.pdf", "ancient halls");

        LegacyScenarioMigrationApplicationService service = service(fixture);
        var result = service.migrate(fixture.scenario().id(), owner);

        assertFalse(result.requiresReupload());
        assertEquals(fixture.scenario().id(), result.scenarioId());
        assertEquals(fixture.ingestPort().documentId(), result.knowledgeDocumentId());
        assertEquals(ScenarioBundleDocumentRole.MAIN_SCENARIO, fixture.bundleRepository().savedBundle().currentRevision().documents().get(0).role());
        assertTrue(fixture.packageRepository().packages.values().stream().anyMatch(pkg -> pkg.packageId().equals(result.packageId())));
    }

    @Test
    void missingSourceRequestsReupload() {
        OwnerPlayerId owner = new OwnerPlayerId(UUID.randomUUID());
        ScenarioScenarioFixture fixture = fixture(owner, "missing.pdf", "missing");
        fixture.storage().clear();

        LegacyScenarioMigrationApplicationService service = service(fixture);
        var result = service.migrate(fixture.scenario().id(), owner);

        assertTrue(result.requiresReupload());
        assertEquals("legacy source is missing; reupload required", result.message());
    }

    @Test
    void reuploadCreatesBundleAndPackageFromReplacementFile() {
        OwnerPlayerId owner = new OwnerPlayerId(UUID.randomUUID());
        ScenarioScenarioFixture fixture = fixture(owner, "missing.pdf", "missing");
        fixture.storage().clear();

        LegacyScenarioMigrationApplicationService service = service(fixture);
        var result = service.reupload(
                fixture.scenario().id(),
                owner,
                new ScenarioUpload(owner, "replacement.pdf", "replacement".getBytes(StandardCharsets.UTF_8)));

        assertFalse(result.requiresReupload());
        assertTrue(result.reupload());
        assertEquals("replacement.pdf", result.sourceFilename());
    }

    private static LegacyScenarioMigrationApplicationService service(ScenarioScenarioFixture fixture) {
        return new LegacyScenarioMigrationApplicationService(
                fixture.scenarioRepository(),
                fixture.storage(),
                fixture.ingestPort(),
                fixture.stateRepository(),
                fixture.bundleService(),
                fixture.compilationService(),
                fixture.lookupPort());
    }

    private static ScenarioScenarioFixture fixture(OwnerPlayerId owner, String filename, String content) {
        InMemoryStorage storage = new InMemoryStorage();
        ScenarioId scenarioId = ScenarioId.generate();
        ScenarioSource source = storage.store(new ScenarioUpload(owner, filename, content.getBytes(StandardCharsets.UTF_8)));
        AdventureScenario scenario = AdventureScenario.recordUpload(scenarioId, owner, source);
        InMemoryScenarioRepository scenarioRepository = new InMemoryScenarioRepository(scenario);
        InMemoryLookup lookup = new InMemoryLookup();
        InMemoryIngestion ingestion = new InMemoryIngestion(lookup);
        InMemoryState state = new InMemoryState();
        InMemoryBundleRepository bundleRepository = new InMemoryBundleRepository();
        InMemoryPackageRepository packageRepository = new InMemoryPackageRepository();
        ScenarioBundleApplicationService bundleService = new ScenarioBundleApplicationService(bundleRepository, lookup);
        ScenarioCompilationApplicationService compilationService = new ScenarioCompilationApplicationService(
                bundleRepository,
                new ScenarioPackageCompilationService(packageRepository),
                packageRepository,
                new ScenarioCompilationProcessManager(new NoopCompilationRepository(), new NoopQueue()),
                new NoopCompilationRepository(),
                new ScenarioSourceExcerptPort() {
                    @Override
                    public List<ResolutionExtractionPort.SourceExcerpt> load(ScenarioSourceBundle bundle) {
                        return List.of();
                    }
                });
        return new ScenarioScenarioFixture(
                scenarioRepository, storage, ingestion, state, lookup, bundleService, compilationService, bundleRepository,
                packageRepository, scenario);
    }

    private record ScenarioScenarioFixture(
            AdventureScenarioRepository scenarioRepository,
            InMemoryStorage storage,
            InMemoryIngestion ingestPort,
            InMemoryState stateRepository,
            InMemoryLookup lookupPort,
            ScenarioBundleApplicationService bundleService,
            ScenarioCompilationApplicationService compilationService,
            InMemoryBundleRepository bundleRepository,
            InMemoryPackageRepository packageRepository,
            AdventureScenario scenario) {}

    private static final class InMemoryStorage implements ScenarioStoragePort {
        private final Map<String, byte[]> archive = new HashMap<>();

        @Override
        public ScenarioSource store(ScenarioUpload upload) {
            String storageKey = "stored-" + upload.originalFilename();
            archive.put(storageKey, upload.content());
            return new ScenarioSource(storageKey, upload.originalFilename(), "hash-" + upload.content().length);
        }

        @Override
        public byte[] read(ScenarioSource source) {
            byte[] content = archive.get(source.storageKey());
            if (content == null) {
                throw new IllegalStateException("missing");
            }
            return content.clone();
        }

        void clear() {
            archive.clear();
        }
    }

    private static final class InMemoryScenarioRepository implements AdventureScenarioRepository {
        private final AdventureScenario scenario;

        private InMemoryScenarioRepository(AdventureScenario scenario) {
            this.scenario = scenario;
        }

        @Override
        public Optional<AdventureScenario> findById(ScenarioId scenarioId) {
            return scenario.id().equals(scenarioId) ? Optional.of(scenario) : Optional.empty();
        }

        @Override
        public void save(AdventureScenario scenario) {}
    }

    private static final class InMemoryLookup implements KnowledgeDocumentLookupPort {
        private final Map<KnowledgeDocumentId, KnowledgeDocumentRecord> records = new HashMap<>();

        @Override
        public List<KnowledgeDocumentRecord> findOwnedDocuments(UUID ownerPlayerId) {
            return List.copyOf(records.values());
        }

        KnowledgeDocumentRecord add(KnowledgeDocumentId id, OwnerPlayerId owner, String originalFilename) {
            KnowledgeDocumentRecord record = new KnowledgeDocumentRecord(
                    id, KnowledgeDocumentStatus.INDEXED, originalFilename, "STORYBOOK", 1);
            records.put(id, record);
            return record;
        }
    }

    private static final class InMemoryIngestion implements LegacyScenarioIngestionPort {
        private final InMemoryLookup lookup;
        private KnowledgeDocumentId documentId;

        private InMemoryIngestion(InMemoryLookup lookup) {
            this.lookup = lookup;
        }

        @Override
        public ImportedKnowledgeDocument ingest(OwnerPlayerId ownerPlayerId, String originalFilename, byte[] content) {
            documentId = KnowledgeDocumentId.generate();
            lookup.add(documentId, ownerPlayerId, originalFilename);
            return new ImportedKnowledgeDocument(documentId, 1L, "INDEXED");
        }

        KnowledgeDocumentId documentId() {
            return documentId;
        }
    }

    private static final class InMemoryState implements LegacyScenarioMigrationStateRepository {
        private final Map<String, LegacyScenarioMigrationApplicationService.LegacyScenarioMigrationResult> store = new HashMap<>();

        @Override
        public Optional<LegacyScenarioMigrationApplicationService.LegacyScenarioMigrationResult> findByScenarioIdAndSourceHash(
                ScenarioId scenarioId, String sourceHash) {
            return Optional.ofNullable(store.get(key(scenarioId, sourceHash)));
        }

        @Override
        public void save(ScenarioId scenarioId, String sourceHash, LegacyScenarioMigrationApplicationService.LegacyScenarioMigrationResult result) {
            store.put(key(scenarioId, sourceHash), result);
        }

        private String key(ScenarioId scenarioId, String sourceHash) {
            return scenarioId.value() + ":" + sourceHash;
        }
    }

    private static final class InMemoryBundleRepository implements ScenarioBundleRepository {
        private ScenarioSourceBundle bundle;

        @Override
        public Optional<ScenarioSourceBundle> findById(ScenarioBundleId bundleId) {
            return bundle != null && bundle.id().equals(bundleId) ? Optional.of(bundle) : Optional.empty();
        }

        @Override
        public void save(ScenarioSourceBundle bundle) {
            this.bundle = bundle;
        }

        ScenarioSourceBundle savedBundle() {
            return bundle;
        }
    }

    private static final class InMemoryPackageRepository implements ScenarioPackageRepository {
        private final Map<UUID, ScenarioPackage> packages = new HashMap<>();

        @Override
        public Optional<ScenarioPackage> findByInputFingerprint(String fingerprint) {
            return packages.values().stream().filter(pkg -> pkg.inputFingerprint().equals(fingerprint)).findFirst();
        }

        @Override
        public Optional<ScenarioPackage> findById(UUID packageId) {
            return Optional.ofNullable(packages.get(packageId));
        }

        @Override
        public void save(ScenarioPackage scenarioPackage) {
            packages.put(scenarioPackage.packageId(), scenarioPackage);
        }
    }

    private static final class NoopCompilationRepository implements ScenarioCompilationRepository {
        @Override public Optional<com.dndmaster.adventure.domain.scenario.ScenarioCompilation> findById(UUID id) { return Optional.empty(); }
        @Override public Optional<com.dndmaster.adventure.domain.scenario.ScenarioCompilation> findByInputFingerprint(String fingerprint) { return Optional.empty(); }
        @Override public void save(com.dndmaster.adventure.domain.scenario.ScenarioCompilation compilation) {}
        @Override public boolean saveIfLeaseMatches(com.dndmaster.adventure.domain.scenario.ScenarioCompilation compilation, UUID expectedLeaseToken) { return true; }
    }

    private static final class NoopQueue implements WorkQueuePort {
        @Override public void enqueue(WorkEnvelope envelope) {}
        @Override public Optional<Delivery> claim(String workerId, java.time.Duration leaseDuration) { return Optional.empty(); }
        @Override public void acknowledge(Delivery delivery) {}
        @Override public void retry(Delivery delivery, String reason) {}
    }
}
