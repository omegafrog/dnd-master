package com.dndmaster.adventure.application.scenario;

import com.dndmaster.adventure.application.knowledge.KnowledgeDocumentLookupPort;
import com.dndmaster.adventure.application.scenario.compilation.ScenarioCompilationApplicationService;
import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import com.dndmaster.adventure.domain.scenario.OwnerPlayerId;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleDocumentRole;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleId;
import com.dndmaster.adventure.domain.scenario.ScenarioId;
import com.dndmaster.adventure.domain.scenario.ScenarioSource;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class LegacyScenarioMigrationApplicationService {
    private static final Duration INDEXING_WAIT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration INDEXING_WAIT_INTERVAL = Duration.ofMillis(100);

    private final AdventureScenarioRepository scenarioRepository;
    private final ScenarioStoragePort storagePort;
    private final LegacyScenarioIngestionPort ingestionPort;
    private final LegacyScenarioMigrationStateRepository stateRepository;
    private final ScenarioBundleApplicationService bundleService;
    private final ScenarioCompilationApplicationService compilationService;
    private final KnowledgeDocumentLookupPort lookupPort;

    public LegacyScenarioMigrationApplicationService(
            AdventureScenarioRepository scenarioRepository,
            ScenarioStoragePort storagePort,
            LegacyScenarioIngestionPort ingestionPort,
            LegacyScenarioMigrationStateRepository stateRepository,
            ScenarioBundleApplicationService bundleService,
            ScenarioCompilationApplicationService compilationService,
            KnowledgeDocumentLookupPort lookupPort) {
        this.scenarioRepository = Objects.requireNonNull(scenarioRepository, "scenario repository must not be null");
        this.storagePort = Objects.requireNonNull(storagePort, "storage port must not be null");
        this.ingestionPort = Objects.requireNonNull(ingestionPort, "ingestion port must not be null");
        this.stateRepository = Objects.requireNonNull(stateRepository, "state repository must not be null");
        this.bundleService = Objects.requireNonNull(bundleService, "bundle service must not be null");
        this.compilationService = Objects.requireNonNull(compilationService, "compilation service must not be null");
        this.lookupPort = Objects.requireNonNull(lookupPort, "lookup port must not be null");
    }

    public LegacyScenarioMigrationResult migrate(ScenarioId scenarioId, OwnerPlayerId ownerPlayerId) {
        return migrate(loadOwned(scenarioId, ownerPlayerId), false, null);
    }

    public LegacyScenarioMigrationResult reupload(ScenarioId scenarioId, OwnerPlayerId ownerPlayerId, ScenarioUpload upload) {
        Objects.requireNonNull(upload, "upload must not be null");
        return migrate(loadOwned(scenarioId, ownerPlayerId), true, upload);
    }

    private LegacyScenarioMigrationResult migrate(
            com.dndmaster.adventure.domain.scenario.AdventureScenario scenario,
            boolean usingReupload,
            ScenarioUpload reupload) {
        ScenarioSource source;
        if (usingReupload) {
            source = storagePort.store(reupload);
            var cached = stateRepository.findByScenarioIdAndSourceHash(scenario.id(), source.contentHash());
            if (cached.isPresent()) {
                return cached.get();
            }
        } else {
            source = scenario.source();
            var cached = stateRepository.findByScenarioIdAndSourceHash(scenario.id(), source.contentHash());
            if (cached.isPresent()) {
                return cached.get();
            }
        }
        byte[] content;
        try {
            content = storagePort.read(source);
        } catch (RuntimeException exception) {
            return LegacyScenarioMigrationResult.requiresReupload(scenario.id(), source.originalFilename(),
                    "legacy source is missing; reupload required");
        }

        LegacyScenarioIngestionPort.ImportedKnowledgeDocument imported =
                ingestionPort.ingest(scenario.ownerPlayerId(), source.originalFilename(), content);
        KnowledgeDocumentId knowledgeDocumentId = imported.knowledgeDocumentId();
        awaitReadyDocument(scenario.ownerPlayerId(), knowledgeDocumentId);
        var bundle = bundleService.createBundle(
                scenario.ownerPlayerId(),
                List.of(new BundleDocumentDraft(knowledgeDocumentId, ScenarioBundleDocumentRole.MAIN_SCENARIO)));
        var scenarioPackage = compilationService.compile(bundle.id(), scenario.ownerPlayerId());
        LegacyScenarioMigrationResult result = LegacyScenarioMigrationResult.migrated(
                scenario.id(),
                bundle.id(),
                scenarioPackage.packageId(),
                knowledgeDocumentId,
                usingReupload,
                source.originalFilename());
        stateRepository.save(scenario.id(), source.contentHash(), result);
        return result;
    }

    private void awaitReadyDocument(OwnerPlayerId ownerPlayerId, KnowledgeDocumentId documentId) {
        Instant deadline = Instant.now().plus(INDEXING_WAIT_TIMEOUT);
        while (Instant.now().isBefore(deadline)) {
            var record = lookupPort.findOwnedDocuments(ownerPlayerId.value()).stream()
                    .filter(candidate -> candidate.knowledgeDocumentId().equals(documentId))
                    .findFirst()
                    .orElse(null);
            if (record != null && isUsable(record.status())) {
                return;
            }
            try {
                Thread.sleep(INDEXING_WAIT_INTERVAL.toMillis());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("legacy migration interrupted", exception);
            }
        }
        throw new IllegalStateException("legacy source document did not become ready");
    }

    private static boolean isUsable(com.dndmaster.adventure.application.knowledge.KnowledgeDocumentStatus status) {
        return switch (status) {
            case EXTRACTED, INDEXED, PARTIAL_AWAITING_CONFIRMATION, PARTIAL_CONFIRMED -> true;
            default -> false;
        };
    }

    private com.dndmaster.adventure.domain.scenario.AdventureScenario loadOwned(
            ScenarioId scenarioId, OwnerPlayerId ownerPlayerId) {
        com.dndmaster.adventure.domain.scenario.AdventureScenario scenario = scenarioRepository.findById(scenarioId)
                .orElseThrow(() -> new IllegalStateException("legacy scenario not found"));
        scenario.authorizeAccess(new com.dndmaster.adventure.domain.scenario.RequestingPlayerId(ownerPlayerId.value()));
        return scenario;
    }

    public record LegacyScenarioMigrationResult(
            ScenarioId scenarioId,
            UUID bundleId,
            UUID packageId,
            KnowledgeDocumentId knowledgeDocumentId,
            boolean reupload,
            boolean requiresReupload,
            String sourceFilename,
            String message) {
        static LegacyScenarioMigrationResult migrated(
                ScenarioId scenarioId,
                ScenarioBundleId bundleId,
                UUID packageId,
                KnowledgeDocumentId knowledgeDocumentId,
                boolean reupload,
                String sourceFilename) {
            return new LegacyScenarioMigrationResult(
                    scenarioId, bundleId.value(), packageId, knowledgeDocumentId, reupload, false, sourceFilename,
                    reupload ? "legacy scenario reupload migrated" : "legacy scenario migrated");
        }

        static LegacyScenarioMigrationResult requiresReupload(
                ScenarioId scenarioId, String sourceFilename, String message) {
            return new LegacyScenarioMigrationResult(
                    scenarioId, null, null, null, false, true, sourceFilename, message);
        }
    }
}
