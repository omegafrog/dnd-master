package com.dndmaster.ruleknowledge.application.pipeline;

import com.dndmaster.ruleknowledge.application.indexing.IndexingCommand;
import com.dndmaster.ruleknowledge.application.indexing.IndexingFailedException;
import com.dndmaster.ruleknowledge.application.indexing.RulebookIndexingApplicationService;
import com.dndmaster.ruleknowledge.application.registration.RulebookContentExtractor;
import com.dndmaster.ruleknowledge.application.registration.RulebookFileStorage;
import com.dndmaster.ruleknowledge.application.registration.RulebookRegistrationApplicationService;
import com.dndmaster.ruleknowledge.application.registration.RulebookRegistrationRepository;
import com.dndmaster.ruleknowledge.application.registration.RulebookUploadHash;
import com.dndmaster.ruleknowledge.application.registration.StoredRulebookFile;
import com.dndmaster.ruleknowledge.application.registration.StoredRulebookRegistration;
import com.dndmaster.ruleknowledge.domain.index.IndexKey;
import com.dndmaster.ruleknowledge.domain.rulebook.DocumentType;
import com.dndmaster.ruleknowledge.domain.rulebook.ExtractionResult;
import com.dndmaster.ruleknowledge.domain.rulebook.FileSize;
import com.dndmaster.ruleknowledge.domain.rulebook.ProcessingStatus;
import com.dndmaster.ruleknowledge.domain.rulebook.Rulebook;
import com.dndmaster.ruleknowledge.domain.rulebook.RulebookId;
import java.time.Instant;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class RulebookPipelineApplicationService implements RulebookUploadProcessor {
    private static final Duration PROCESSING_LEASE = Duration.ofMinutes(10);
    private static final int PENDING_BATCH_SIZE = 10;

    private final RulebookRegistrationApplicationService registrationService;
    private final RulebookRegistrationRepository registrationRepository;
    private final RulebookFileStorage fileStorage;
    private final RulebookContentExtractor contentExtractor;
    private final RulebookIndexingApplicationService indexingService;
    private final int embeddingDimension;
    private final ConcurrentMap<String, StoredRulebookRegistration> replayedUploads = new ConcurrentHashMap<>();

    public RulebookPipelineApplicationService(
            RulebookRegistrationApplicationService registrationService,
            RulebookRegistrationRepository registrationRepository,
            RulebookFileStorage fileStorage,
            RulebookContentExtractor contentExtractor,
            RulebookIndexingApplicationService indexingService,
            int embeddingDimension) {
        this.registrationService = Objects.requireNonNull(registrationService, "registrationService must not be null");
        this.registrationRepository = Objects.requireNonNull(registrationRepository, "registrationRepository must not be null");
        this.fileStorage = Objects.requireNonNull(fileStorage, "fileStorage must not be null");
        this.contentExtractor = Objects.requireNonNull(contentExtractor, "contentExtractor must not be null");
        this.indexingService = Objects.requireNonNull(indexingService, "indexingService must not be null");
        if (embeddingDimension <= 0) {
            throw new IllegalArgumentException("embeddingDimension must be positive");
        }
        this.embeddingDimension = embeddingDimension;
    }

    @Override
    public RulebookProcessingResult process(UploadRulebookCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        String contentHash = RulebookUploadHash.sha256(command.fileContent());

        StoredRulebookRegistration replayed = replayedUploads.get(command.operationKey());
        if (replayed != null) {
            if (!replayed.ownerPlayerId().equals(command.ownerPlayerId())
                    || !replayed.contentHash().equals(contentHash)) {
                throw new RulebookPipelineException("conflict: same idempotency key with different file");
            }
            return new RulebookProcessingResult(replayed.rulebookId(), replayed.processingStatus(), List.of());
        }

        Optional<StoredRulebookRegistration> existing = registrationRepository.findByOperationKey(command.operationKey());
        if (existing.isPresent()) {
            StoredRulebookRegistration previous = existing.get();
            if (!previous.ownerPlayerId().equals(command.ownerPlayerId())
                    || !previous.contentHash().equals(contentHash)) {
                throw new RulebookPipelineException("conflict: same idempotency key with different file");
            }
            replayedUploads.putIfAbsent(command.operationKey(), previous);
            return new RulebookProcessingResult(previous.rulebookId(), previous.processingStatus(), List.of());
        }

        Optional<StoredRulebookRegistration> duplicate =
                registrationRepository.findByOwnerAndContentHash(command.ownerPlayerId(), contentHash);
        if (duplicate.isPresent()) {
            StoredRulebookRegistration previous = duplicate.get();
            StoredRulebookRegistration replayedRegistration = new StoredRulebookRegistration(
                    previous.rulebookId(),
                    previous.ownerPlayerId(),
                    command.operationKey(),
                    previous.contentHash(),
                    previous.format(),
                    previous.fileSize(),
                    previous.storageKey(),
                    previous.processingStatus(),
                    previous.extractionStatus(),
                    previous.extractedContent(),
                    previous.missingLocations(),
                    previous.failureCode(),
                    previous.version(),
                    previous.createdAt(),
                    previous.updatedAt(),
                    previous.documentType(),
                    previous.originalFilename());
            registrationRepository.save(replayedRegistration);
            replayedUploads.put(command.operationKey(), replayedRegistration);
            return new RulebookProcessingResult(previous.rulebookId(), previous.processingStatus(), List.of());
        }

        RulebookId rulebookId = RulebookId.generate();
        StoredRulebookFile storedFile = fileStorage.store(rulebookId, Arrays.copyOf(command.fileContent(), command.fileContent().length));
        StoredRulebookRegistration queued = new StoredRulebookRegistration(
                rulebookId,
                command.ownerPlayerId(),
                command.operationKey(),
                contentHash,
                command.format(),
                command.fileContent().length,
                storedFile.key(),
                ProcessingStatus.QUEUED,
                null,
                null,
                List.of(),
                null,
                0L,
                Instant.now(),
                Instant.now(),
                command.documentType(),
                command.originalFilename());
        registrationRepository.save(queued);
        replayedUploads.put(command.operationKey(), queued);
        return new RulebookProcessingResult(rulebookId, ProcessingStatus.QUEUED, List.of());
    }

    public List<RulebookProcessingResult> processPending() {
        Instant leaseCutoff = Instant.now().minus(PROCESSING_LEASE);
        return registrationRepository.claimPending(leaseCutoff, PENDING_BATCH_SIZE).stream()
                .map(this::processClaimedRegistration)
                .toList();
    }

    public RulebookProcessingResult retry(RulebookId rulebookId) {
        StoredRulebookRegistration registration = registrationRepository.findById(rulebookId)
                .orElseThrow(() -> new IllegalArgumentException("knowledge document not found"));
        if (registration.processingStatus() != ProcessingStatus.FAILED) {
            throw new IllegalStateException("only failed document can be retried");
        }
        StoredRulebookRegistration queued = withStatus(registration, ProcessingStatus.QUEUED, null, null, null, null);
        registrationRepository.save(queued);
        replayedUploads.put(queued.operationKey(), queued);
        return new RulebookProcessingResult(rulebookId, ProcessingStatus.QUEUED, List.of());
    }

    private RulebookProcessingResult processClaimedRegistration(StoredRulebookRegistration registration) {
        if (registration.processingStatus() != ProcessingStatus.PROCESSING) {
            throw new IllegalStateException("only claimed document can be processed");
        }
        try {
            byte[] storedContent = fileStorage.read(new StoredRulebookFile(registration.storageKey()));
            ExtractionResult extractionResult = contentExtractor.extract(
                    registration.format(), Arrays.copyOf(storedContent, storedContent.length));
            Rulebook rulebook = Rulebook.acceptUpload(
                    registration.rulebookId(),
                    registration.ownerPlayerId(),
                    registration.format(),
                    new FileSize(registration.fileSize()));
            rulebook.recordExtraction(extractionResult);
            if (!rulebook.isEligibleForSplitting()) {
                return fail(registration, extractionResult, describeExtractionFailure(extractionResult));
            }
            attemptIndexing(rulebook, registration);
            StoredRulebookRegistration indexed = withStatus(
                    registration,
                    ProcessingStatus.INDEXED,
                    extractionResult,
                    null,
                    extractionResult.content().orElse(null),
                    extractionResult.missingLocations());
            registrationRepository.save(indexed);
            replayedUploads.put(indexed.operationKey(), indexed);
            return new RulebookProcessingResult(registration.rulebookId(), ProcessingStatus.INDEXED, List.of());
        } catch (IndexingFailedException exception) {
            return fail(registration, null, describeFailure(exception));
        } catch (RuntimeException exception) {
            return fail(registration, null, describeFailure(exception));
        }
    }

    private void attemptIndexing(Rulebook rulebook, StoredRulebookRegistration registration) {
        IndexKey indexKey = new IndexKey(registration.rulebookId(), registration.contentHash(), "ollama-embedding", "v1");
        IndexingCommand indexingCommand = new IndexingCommand(rulebook, indexKey, embeddingDimension);
        try {
            indexingService.indexContent(indexingCommand);
        } catch (IllegalStateException exception) {
            if (exception.getMessage() != null && exception.getMessage().contains("failed index requires explicit retry")) {
                indexingService.retryIndexing(indexingCommand);
                return;
            }
            throw exception;
        }
    }

    private RulebookProcessingResult fail(
            StoredRulebookRegistration registration, ExtractionResult extractionResult, String reason) {
        StoredRulebookRegistration failed = withStatus(
                registration,
                ProcessingStatus.FAILED,
                extractionResult,
                reason,
                extractionResult != null ? extractionResult.content().orElse(null) : registration.extractedContent(),
                extractionResult != null ? extractionResult.missingLocations() : registration.missingLocations());
        registrationRepository.save(failed);
        replayedUploads.put(failed.operationKey(), failed);
        return new RulebookProcessingResult(registration.rulebookId(), ProcessingStatus.FAILED, List.of(reason));
    }

    private static String describeExtractionFailure(ExtractionResult extractionResult) {
        return extractionResult.failure()
                .map(failure -> "extraction failed: " + failure.name())
                .orElse("partial extraction requires confirmation");
    }

    private static String describeFailure(RuntimeException exception) {
        String message = exception.getMessage();
        return message != null && !message.isBlank() ? message : "processing failed";
    }

    private static StoredRulebookRegistration withStatus(
            StoredRulebookRegistration registration,
            ProcessingStatus status,
            ExtractionResult extractionResult,
            String failureCode,
            String extractedContent,
            List<String> missingLocations) {
        return new StoredRulebookRegistration(
                registration.rulebookId(),
                registration.ownerPlayerId(),
                registration.operationKey(),
                registration.contentHash(),
                registration.format(),
                registration.fileSize(),
                registration.storageKey(),
                status,
                extractionResult != null ? extractionResult.status() : registration.extractionStatus(),
                extractedContent != null ? extractedContent : registration.extractedContent(),
                missingLocations != null ? missingLocations : registration.missingLocations(),
                failureCode,
                registration.version() + 1,
                registration.createdAt(),
                Instant.now(),
                registration.documentType(),
                registration.originalFilename());
    }
}
