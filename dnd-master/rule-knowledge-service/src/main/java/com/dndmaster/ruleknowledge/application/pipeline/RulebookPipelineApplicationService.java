package com.dndmaster.ruleknowledge.application.pipeline;

import com.dndmaster.ruleknowledge.application.indexing.IndexingCommand;
import com.dndmaster.ruleknowledge.application.indexing.IndexingFailedException;
import com.dndmaster.ruleknowledge.application.indexing.RulebookIndexingApplicationService;
import com.dndmaster.ruleknowledge.application.registration.RulebookContentExtractor;
import com.dndmaster.ruleknowledge.application.registration.RulebookFileStorage;
import com.dndmaster.ruleknowledge.application.registration.RulebookRegistrationApplicationService;
import com.dndmaster.ruleknowledge.application.registration.RulebookRegistrationRepository;
import com.dndmaster.ruleknowledge.application.registration.StoredRulebookFile;
import com.dndmaster.ruleknowledge.application.registration.StoredRulebookRegistration;
import com.dndmaster.ruleknowledge.domain.index.IndexKey;
import com.dndmaster.ruleknowledge.domain.rulebook.DocumentType;
import com.dndmaster.ruleknowledge.domain.rulebook.ExtractionResult;
import com.dndmaster.ruleknowledge.domain.rulebook.FileSize;
import com.dndmaster.ruleknowledge.domain.rulebook.ProcessingStatus;
import com.dndmaster.ruleknowledge.domain.rulebook.Rulebook;
import com.dndmaster.ruleknowledge.domain.rulebook.RulebookId;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class RulebookPipelineApplicationService implements RulebookUploadProcessor {
    private final RulebookRegistrationApplicationService registrationService;
    private final RulebookRegistrationRepository registrationRepository;
    private final RulebookFileStorage fileStorage;
    private final RulebookContentExtractor contentExtractor;
    private final RulebookIndexingApplicationService indexingService;
    private final int embeddingDimension;

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

        Optional<StoredRulebookRegistration> existing = registrationRepository.findByOperationKey(command.operationKey());
        if (existing.isPresent()) {
            StoredRulebookRegistration previous = existing.get();
            if (!previous.contentHash().equals(computeHash(command))) {
                throw new RulebookPipelineException("conflict: same idempotency key with different file");
            }
            return new RulebookProcessingResult(previous.rulebookId(), previous.processingStatus(), List.of());
        }

        RulebookId rulebookId = RulebookId.generate();
        StoredRulebookFile storedFile = fileStorage.store(rulebookId, Arrays.copyOf(command.fileContent(), command.fileContent().length));
        StoredRulebookRegistration queued = new StoredRulebookRegistration(
                rulebookId,
                command.ownerPlayerId(),
                command.operationKey(),
                computeHash(command),
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
        return new RulebookProcessingResult(rulebookId, ProcessingStatus.QUEUED, List.of());
    }

    public List<RulebookProcessingResult> processPending() {
        return registrationRepository.findByProcessingStatuses(List.of(ProcessingStatus.QUEUED)).stream()
                .map(this::processStoredRegistration)
                .toList();
    }

    public RulebookProcessingResult retry(RulebookId rulebookId) {
        StoredRulebookRegistration registration = registrationRepository.findById(rulebookId)
                .orElseThrow(() -> new IllegalArgumentException("knowledge document not found"));
        if (registration.processingStatus() == ProcessingStatus.INDEXED) {
            return new RulebookProcessingResult(rulebookId, ProcessingStatus.INDEXED, List.of());
        }
        if (registration.processingStatus() != ProcessingStatus.FAILED) {
            throw new IllegalStateException("only failed document can be retried");
        }
        StoredRulebookRegistration queued = withStatus(registration, ProcessingStatus.QUEUED, null, null, null, null);
        registrationRepository.save(queued);
        return processStoredRegistration(queued);
    }

    private RulebookProcessingResult processStoredRegistration(StoredRulebookRegistration registration) {
        if (registration.processingStatus() != ProcessingStatus.QUEUED) {
            throw new IllegalStateException("only queued document can be processed");
        }
        StoredRulebookRegistration processing = withStatus(registration, ProcessingStatus.PROCESSING, null, null, null, null);
        registrationRepository.save(processing);
        try {
            byte[] storedContent = fileStorage.read(new StoredRulebookFile(processing.storageKey()));
            ExtractionResult extractionResult = contentExtractor.extract(
                    processing.format(), Arrays.copyOf(storedContent, storedContent.length));
            Rulebook rulebook = Rulebook.acceptUpload(
                    processing.rulebookId(),
                    processing.ownerPlayerId(),
                    processing.format(),
                    new FileSize(processing.fileSize()));
            rulebook.recordExtraction(extractionResult);
            if (!rulebook.isEligibleForSplitting()) {
                return fail(processing, extractionResult, describeExtractionFailure(extractionResult));
            }
            attemptIndexing(rulebook, processing);
            StoredRulebookRegistration indexed = withStatus(
                    processing,
                    ProcessingStatus.INDEXED,
                    extractionResult,
                    null,
                    extractionResult.content().orElse(null),
                    extractionResult.missingLocations());
            registrationRepository.save(indexed);
            return new RulebookProcessingResult(processing.rulebookId(), ProcessingStatus.INDEXED, List.of());
        } catch (IndexingFailedException exception) {
            return fail(processing, null, describeFailure(exception));
        } catch (RuntimeException exception) {
            return fail(processing, null, describeFailure(exception));
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
        return new RulebookProcessingResult(registration.rulebookId(), ProcessingStatus.FAILED, List.of(reason));
    }

    private static String computeHash(UploadRulebookCommand command) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(command.ownerPlayerId().value().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(command.documentType().name().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(command.format().name().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            digest.update((byte) 0);
            return HexFormat.of().formatHex(digest.digest(command.fileContent()));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required", e);
        }
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
