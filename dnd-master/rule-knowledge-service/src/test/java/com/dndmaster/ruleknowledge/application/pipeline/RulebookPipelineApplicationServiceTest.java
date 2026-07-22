package com.dndmaster.ruleknowledge.application.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndmaster.ruleknowledge.application.indexing.ChunkEmbedding;
import com.dndmaster.ruleknowledge.application.indexing.EmbeddingPort;
import com.dndmaster.ruleknowledge.application.indexing.IndexingCommand;
import com.dndmaster.ruleknowledge.application.indexing.RulebookIndexRepository;
import com.dndmaster.ruleknowledge.application.indexing.RulebookIndexingApplicationService;
import com.dndmaster.ruleknowledge.application.indexing.StructureDetectionPort;
import com.dndmaster.ruleknowledge.application.registration.RulebookContentExtractor;
import com.dndmaster.ruleknowledge.application.registration.RulebookFileStorage;
import com.dndmaster.ruleknowledge.application.registration.RulebookRegistrationApplicationService;
import com.dndmaster.ruleknowledge.application.registration.StoredRulebookFile;
import com.dndmaster.ruleknowledge.application.registration.StoredRulebookRegistration;
import com.dndmaster.ruleknowledge.domain.index.ChunkId;
import com.dndmaster.ruleknowledge.domain.index.EmbeddedRulebookChunk;
import com.dndmaster.ruleknowledge.domain.index.IndexKey;
import com.dndmaster.ruleknowledge.domain.index.IndexStatus;
import com.dndmaster.ruleknowledge.domain.index.RulebookIndex;
import com.dndmaster.ruleknowledge.domain.rulebook.DocumentType;
import com.dndmaster.ruleknowledge.domain.rulebook.ExtractionResult;
import com.dndmaster.ruleknowledge.domain.rulebook.OwnerPlayerId;
import com.dndmaster.ruleknowledge.domain.rulebook.ProcessingStatus;
import com.dndmaster.ruleknowledge.domain.rulebook.RulebookFormat;
import com.dndmaster.ruleknowledge.domain.rulebook.RulebookId;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RulebookPipelineApplicationServiceTest {
    @Test
    void uploadQueuesFirstAndWorkerProcessesLater() {
        TestHarness harness = new TestHarness();

        RulebookProcessingResult queued = harness.service.process(command("upload-1", "alpha"));

        assertEquals(ProcessingStatus.QUEUED, queued.status());
        assertEquals(ProcessingStatus.QUEUED, harness.repository.findByOperationKey("upload-1").orElseThrow().processingStatus());
        assertEquals(0, harness.extractor.calls);
        assertEquals(0, harness.embeddingPort.calls);

        List<RulebookProcessingResult> processed = harness.service.processPending();

        assertEquals(1, processed.size());
        assertEquals(ProcessingStatus.INDEXED, processed.get(0).status());
        assertEquals(ProcessingStatus.INDEXED, harness.repository.findByOperationKey("upload-1").orElseThrow().processingStatus());
        assertEquals(1, harness.extractor.calls);
        assertEquals(1, harness.embeddingPort.calls);
    }

    @Test
    void retryOnlyReprocessesFailedDocument() {
        TestHarness harness = new TestHarness();
        RulebookProcessingResult first = harness.service.process(command("upload-1", "alpha"));
        RulebookProcessingResult second = harness.service.process(command("upload-2", "beta"));

        assertEquals(ProcessingStatus.QUEUED, first.status());
        assertEquals(ProcessingStatus.QUEUED, second.status());

        harness.service.processPending();

        StoredRulebookRegistration alpha = harness.repository.findByOperationKey("upload-1").orElseThrow();
        StoredRulebookRegistration beta = harness.repository.findByOperationKey("upload-2").orElseThrow();
        assertEquals(ProcessingStatus.INDEXED, alpha.processingStatus());
        assertEquals(ProcessingStatus.FAILED, beta.processingStatus());
        assertEquals(1, harness.embeddingPort.failures);

        RulebookProcessingResult retried = harness.service.retry(beta.rulebookId());

        assertEquals(ProcessingStatus.INDEXED, retried.status());
        assertEquals(ProcessingStatus.INDEXED, harness.repository.findById(beta.rulebookId()).orElseThrow().processingStatus());
        assertEquals(3, harness.embeddingPort.calls);
        assertEquals(1, harness.embeddingPort.failures);
        assertEquals(IndexStatus.READY, harness.indexingRepository.load(beta.rulebookId()).status());

        RulebookProcessingResult duplicateRetry = harness.service.retry(beta.rulebookId());
        assertEquals(ProcessingStatus.INDEXED, duplicateRetry.status());
        assertEquals(3, harness.embeddingPort.calls);
    }

    @Test
    void retryRejectsNonFailedDocument() {
        TestHarness harness = new TestHarness();
        RulebookProcessingResult queued = harness.service.process(command("upload-1", "alpha"));

        assertEquals(ProcessingStatus.QUEUED, queued.status());
        assertThrows(IllegalStateException.class, () -> harness.service.retry(queued.rulebookId()));
    }

    private static UploadRulebookCommand command(String operationKey, String content) {
        return new UploadRulebookCommand(
                operationKey,
                new OwnerPlayerId(UUID.fromString("36c6b6fd-2f36-4b79-9a91-614f9e35bd91")),
                DocumentType.RULEBOOK,
                RulebookFormat.TXT,
                content.getBytes(StandardCharsets.UTF_8),
                content + ".txt");
    }

    private static final class TestHarness {
        private final InMemoryRulebookRegistrationRepository repository = new InMemoryRulebookRegistrationRepository();
        private final InMemoryRulebookIndexRepository indexingRepository = new InMemoryRulebookIndexRepository();
        private final RecordingExtractor extractor = new RecordingExtractor();
        private final RecordingEmbeddingPort embeddingPort = new RecordingEmbeddingPort("beta");
        private final RulebookPipelineApplicationService service;

        private TestHarness() {
            RulebookFileStorage storage = new InMemoryFileStorage();
            RulebookRegistrationApplicationService registrationService =
                    new RulebookRegistrationApplicationService(storage, extractor);
            RulebookIndexingApplicationService indexingService = new RulebookIndexingApplicationService(
                    indexingRepository,
                    embeddingPort,
                    content -> StructureDetectionPort.DetectedStructure.none(),
                    4_000);
            this.service = new RulebookPipelineApplicationService(
                    registrationService,
                    repository,
                    storage,
                    extractor,
                    indexingService,
                    3);
        }
    }

    private static final class InMemoryFileStorage implements RulebookFileStorage {
        private final Map<String, byte[]> files = new HashMap<>();

        @Override
        public StoredRulebookFile store(RulebookId rulebookId, byte[] content) {
            String key = rulebookId.value().toString();
            files.put(key, content.clone());
            return new StoredRulebookFile(key);
        }

        @Override
        public byte[] read(StoredRulebookFile storedFile) {
            byte[] content = files.get(storedFile.key());
            return content == null ? new byte[0] : content.clone();
        }
    }

    private static final class RecordingExtractor implements RulebookContentExtractor {
        private int calls;

        @Override
        public ExtractionResult extract(RulebookFormat format, byte[] content) {
            calls++;
            return ExtractionResult.success(new String(content, StandardCharsets.UTF_8));
        }
    }

    private static final class RecordingEmbeddingPort implements EmbeddingPort {
        private final String failingMarker;
        private final Set<String> failedOnce = new java.util.HashSet<>();
        private int calls;
        private int failures;

        private RecordingEmbeddingPort(String failingMarker) {
            this.failingMarker = failingMarker;
        }

        @Override
        public List<ChunkEmbedding> embed(List<com.dndmaster.ruleknowledge.domain.index.RulebookChunk> chunks, String embeddingModel, int expectedDimension) {
            calls++;
            String marker = chunks.stream().map(com.dndmaster.ruleknowledge.domain.index.RulebookChunk::content).reduce("", String::concat);
            if (marker.contains(failingMarker) && failedOnce.add(marker)) {
                failures++;
                throw new IllegalStateException("mock embedding failure");
            }
            return chunks.stream().map(chunk -> new ChunkEmbedding(chunk.chunkId(), new float[]{1f, 0f, 0f})).toList();
        }
    }

    private static final class InMemoryRulebookIndexRepository implements RulebookIndexRepository {
        private final Map<IndexKey, RulebookIndex> indexes = new LinkedHashMap<>();
        private final List<IndexStatus> savedStatuses = new ArrayList<>();

        @Override
        public RulebookIndex loadOrCreate(IndexKey key, java.util.function.Supplier<RulebookIndex> newIndex) {
            return indexes.computeIfAbsent(key, ignored -> newIndex.get());
        }

        @Override
        public void save(RulebookIndex index) {
            indexes.put(index.key(), index);
            savedStatuses.add(index.status());
        }

        @Override
        public void saveComplete(RulebookIndex index, List<EmbeddedRulebookChunk> chunks) {
            indexes.put(index.key(), index);
            savedStatuses.add(index.status());
        }

        private RulebookIndex load(RulebookId rulebookId) {
            return indexes.values().stream()
                    .filter(index -> index.key().rulebookId().equals(rulebookId))
                    .findFirst()
                    .orElseThrow();
        }
    }

    private static final class InMemoryRulebookRegistrationRepository implements com.dndmaster.ruleknowledge.application.registration.RulebookRegistrationRepository {
        private final Map<String, StoredRulebookRegistration> byOperationKey = new LinkedHashMap<>();
        private final Map<RulebookId, StoredRulebookRegistration> byId = new LinkedHashMap<>();

        @Override
        public Optional<StoredRulebookRegistration> findById(RulebookId id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public Optional<StoredRulebookRegistration> findByOperationKey(String operationKey) {
            return Optional.ofNullable(byOperationKey.get(operationKey));
        }

        @Override
        public List<StoredRulebookRegistration> findByOwner(OwnerPlayerId owner) {
            return byId.values().stream()
                    .filter(registration -> owner == null || registration.ownerPlayerId().equals(owner))
                    .toList();
        }

        @Override
        public List<StoredRulebookRegistration> findByProcessingStatuses(List<ProcessingStatus> statuses) {
            return byId.values().stream()
                    .filter(registration -> statuses.contains(registration.processingStatus()))
                    .toList();
        }

        @Override
        public void save(StoredRulebookRegistration registration) {
            byOperationKey.put(registration.operationKey(), registration);
            byId.put(registration.rulebookId(), registration);
        }
    }
}
