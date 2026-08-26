package com.dndmaster.ruleknowledge.application.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.any;

import com.dndmaster.ruleknowledge.application.indexing.EmbeddingPort;
import com.dndmaster.ruleknowledge.application.indexing.RulebookIndexRepository;
import com.dndmaster.ruleknowledge.application.indexing.RulebookIndexingApplicationService;
import com.dndmaster.ruleknowledge.application.indexing.StructureDetectionPort;
import com.dndmaster.ruleknowledge.application.preprocessing.PreprocessingArtifactManifest;
import com.dndmaster.ruleknowledge.application.preprocessing.PreprocessingPageState;
import com.dndmaster.ruleknowledge.application.preprocessing.PreprocessingProcessPort;
import com.dndmaster.ruleknowledge.application.preprocessing.PreprocessingRetryRequest;
import com.dndmaster.ruleknowledge.application.preprocessing.PreprocessingRunRequest;
import com.dndmaster.ruleknowledge.application.preprocessing.PreprocessingRunResult;
import com.dndmaster.ruleknowledge.application.preprocessing.PreprocessingStatusRequest;
import com.dndmaster.ruleknowledge.application.publication.RagExtractionPublicationService;
import com.dndmaster.ruleknowledge.application.publication.EmbeddedPublishedRagChunk;
import com.dndmaster.ruleknowledge.application.publication.ExtractionPublicationStatus;
import com.dndmaster.ruleknowledge.application.publication.RagExtractionPublicationRepository;
import com.dndmaster.ruleknowledge.application.publication.RagExtractionPublicationRequest;
import com.dndmaster.ruleknowledge.application.registration.RulebookContentExtractor;
import com.dndmaster.ruleknowledge.application.registration.RulebookFileStorage;
import com.dndmaster.ruleknowledge.application.registration.RulebookRegistrationApplicationService;
import com.dndmaster.ruleknowledge.application.registration.RulebookRegistrationRepository;
import com.dndmaster.ruleknowledge.application.registration.SourcePreviewExtractor;
import com.dndmaster.ruleknowledge.application.registration.StoredRulebookFile;
import com.dndmaster.ruleknowledge.application.registration.StoredRulebookRegistration;
import com.dndmaster.ruleknowledge.domain.index.RulebookIndex;
import com.dndmaster.ruleknowledge.domain.rulebook.DocumentType;
import com.dndmaster.ruleknowledge.domain.rulebook.OwnerPlayerId;
import com.dndmaster.ruleknowledge.domain.rulebook.ProcessingStatus;
import com.dndmaster.ruleknowledge.domain.rulebook.RulebookFormat;
import com.dndmaster.ruleknowledge.domain.rulebook.RulebookId;
import com.dndmaster.ruleknowledge.domain.rulebook.SourcePreviewResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class Rag017RetryFlowTest {
    @Test
    void selectedPageRetryPublishesFreshVersionAndDuplicateRequestIsIdempotent() throws Exception {
        InMemoryRegistrationRepository registrations = new InMemoryRegistrationRepository();
        InMemoryStorage storage = new InMemoryStorage();
        RecordingPreprocessingPort preprocessing = new RecordingPreprocessingPort();
        RecordingPublicationRepository publicationRepository = new RecordingPublicationRepository();
        RagExtractionPublicationService publication = new RagExtractionPublicationService(publicationRepository,
                (chunks, model, dimension) -> chunks.stream().map(chunk ->
                        new com.dndmaster.ruleknowledge.application.indexing.ChunkEmbedding(chunk.chunkId(), new float[] {1f, 0f, 0f})).toList(),
                "mock", 3);
        RulebookIndexingApplicationService indexing = new RulebookIndexingApplicationService(
                mock(RulebookIndexRepository.class), mock(EmbeddingPort.class),
                content -> StructureDetectionPort.DetectedStructure.none(), 100);
        RulebookPipelineApplicationService pipeline = new RulebookPipelineApplicationService(
                new RulebookRegistrationApplicationService(storage, (format, content) -> null), registrations, storage,
                (format, content) -> null, (format, content) -> new SourcePreviewResult("", List.of(), List.of(), List.of()), indexing, 3,
                preprocessing, publication, new com.dndmaster.ruleknowledge.application.preprocessing.PreprocessingArtifactImporter(new com.fasterxml.jackson.databind.ObjectMapper()),
                new com.dndmaster.ruleknowledge.application.preprocessing.InMemoryPreprocessingRetryLeaseRepository());
        UploadRulebookCommand command = new UploadRulebookCommand(
                "retry-upload", new OwnerPlayerId(UUID.randomUUID()), DocumentType.STORYBOOK, RulebookFormat.PDF,
                "pdf".getBytes(), "story.pdf");

        RulebookProcessingResult queued = pipeline.process(command);
        assertEquals(ProcessingStatus.QUEUED, queued.status());
        assertEquals(ProcessingStatus.NEEDS_REVIEW, pipeline.processPending().getFirst().status());
        RulebookId document = queued.rulebookId();

        assertEquals(ProcessingStatus.INDEXED, pipeline.retryPages(document, "retry-request-1", List.of(1)).status());
        assertEquals(ProcessingStatus.INDEXED, pipeline.retryPages(document, "retry-request-1", List.of(1)).status());
        assertEquals(1, preprocessing.retryCalls);
        assertEquals(1, publicationRepository.publishCalls);
    }

    private static final class RecordingPreprocessingPort implements PreprocessingProcessPort {
        private int retryCalls;
        private String sourceHash;

        @Override
        public PreprocessingRunResult preprocess(PreprocessingRunRequest request) {
            sourceHash = request.sourceSha256();
            return result(request.requestId(), "candidate-1", "NEEDS_REVIEW", request.sourceSha256(), request.policyVersion(),
                    new PreprocessingPageState(1, "NEEDS_REVIEW", 1, List.of("AMBIGUOUS_COLUMNS")), new PreprocessingArtifactManifest("b".repeat(64), Map.of()));
        }

        @Override
        public PreprocessingRunResult status(PreprocessingStatusRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PreprocessingRunResult retryPages(PreprocessingRetryRequest request) {
            retryCalls++;
            try {
                Files.createDirectories(request.artifactRoot());
                Path chunks = request.artifactRoot().resolve("chunks.jsonl");
                Files.writeString(chunks, "{\"chunk_id\":\"chunk-1\",\"source_text\":\"A rule\",\"embedding_text\":\"A rule\",\"source_spans\":[{\"page_number\":1}],\"section_path\":[\"Combat\"]}\n");
                return result(request.requestId(), "candidate-1-retry", "READY", sourceHash, "rag-preprocessing-v1",
                        new PreprocessingPageState(1, "VALIDATED", 2, List.of()),
                        new PreprocessingArtifactManifest("c".repeat(64), Map.of("chunks", sha256(chunks)), Map.of("chunks", chunks)));
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        }

        private static String sha256(Path path) throws Exception {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(Files.readAllBytes(path)));
        }

        private static PreprocessingRunResult result(String requestId, String version, String status, String sourceHash,
                String policy, PreprocessingPageState page, PreprocessingArtifactManifest artifacts) {
            return new PreprocessingRunResult(requestId, version, status, sourceHash, policy, List.of(page), artifacts);
        }
    }

    private static final class InMemoryStorage implements RulebookFileStorage {
        private final Map<String, byte[]> files = new LinkedHashMap<>();
        @Override public StoredRulebookFile store(RulebookId id, byte[] content) { files.put(id.value().toString(), content.clone()); return new StoredRulebookFile(id.value().toString()); }
        @Override public byte[] read(StoredRulebookFile file) { return files.get(file.key()).clone(); }
    }

    private static final class RecordingPublicationRepository implements RagExtractionPublicationRepository {
        private int publishCalls;
        @Override public void beginCandidate(RagExtractionPublicationRequest request) {}
        @Override public void publish(RagExtractionPublicationRequest request, List<EmbeddedPublishedRagChunk> chunks) { publishCalls++; }
        @Override public void fail(RagExtractionPublicationRequest request, ExtractionPublicationStatus status, String reason) {}
    }

    private static final class InMemoryRegistrationRepository implements RulebookRegistrationRepository {
        private final Map<RulebookId, StoredRulebookRegistration> values = new LinkedHashMap<>();
        @Override public Optional<StoredRulebookRegistration> findById(RulebookId id) { return Optional.ofNullable(values.get(id)); }
        @Override public Optional<StoredRulebookRegistration> findByOperationKey(String key) { return values.values().stream().filter(value -> value.operationKey().equals(key)).findFirst(); }
        @Override public Optional<StoredRulebookRegistration> findByOwnerAndContentHash(OwnerPlayerId owner, String hash) { return values.values().stream().filter(value -> value.ownerPlayerId().equals(owner) && value.contentHash().equals(hash)).findFirst(); }
        @Override public List<StoredRulebookRegistration> findByOwner(OwnerPlayerId owner) { return values.values().stream().filter(value -> value.ownerPlayerId().equals(owner)).toList(); }
        @Override public List<StoredRulebookRegistration> findByProcessingStatuses(List<ProcessingStatus> statuses) { return values.values().stream().filter(value -> statuses.contains(value.processingStatus())).toList(); }
        @Override public List<StoredRulebookRegistration> claimPending(Instant cutoff, int limit) {
            return values.values().stream().filter(value -> value.processingStatus() == ProcessingStatus.QUEUED).limit(limit).map(value -> copy(value, ProcessingStatus.PROCESSING)).peek(this::save).toList();
        }
        @Override public void save(StoredRulebookRegistration value) { values.put(value.rulebookId(), value); }
        private static StoredRulebookRegistration copy(StoredRulebookRegistration value, ProcessingStatus status) {
            return new StoredRulebookRegistration(value.rulebookId(), value.ownerPlayerId(), value.operationKey(), value.contentHash(), value.format(), value.fileSize(), value.storageKey(), status,
                    value.extractionStatus(), value.extractedContent(), value.missingLocations(), value.failureCode(), value.version() + 1, value.createdAt(), Instant.now(), value.documentType(), value.originalFilename(), value.previewContent(), value.previewWarnings(), value.previewSpans(), value.previewAssets(), value.preprocessingOperationId(), value.candidateExtractionVersion(), value.preprocessingPolicyVersion(), value.preprocessingManifestSha256(), value.preprocessingPages());
        }
    }
}
