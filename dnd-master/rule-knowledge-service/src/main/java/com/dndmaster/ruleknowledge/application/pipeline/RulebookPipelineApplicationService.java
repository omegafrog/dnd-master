package com.dndmaster.ruleknowledge.application.pipeline;

import com.dndmaster.ruleknowledge.application.indexing.*;
import com.dndmaster.ruleknowledge.application.registration.*;
import com.dndmaster.ruleknowledge.domain.index.IndexKey;
import com.dndmaster.ruleknowledge.domain.rulebook.*;
import com.dndmaster.ruleknowledge.domain.index.EmbeddedRulebookChunk;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class RulebookPipelineApplicationService {
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

    public RulebookProcessingResult process(UploadRulebookCommand command) {
        Objects.requireNonNull(command, "command must not be null");

        // 1. Check idempotency
        Optional<StoredRulebookRegistration> existing = registrationRepository.findByOperationKey(command.operationKey());
        if (existing.isPresent()) {
            StoredRulebookRegistration previous = existing.get();
            if (!previous.contentHash().equals(computeHash(command))) {
                throw new RulebookPipelineException("conflict: same idempotency key with different file");
            }
            return new RulebookProcessingResult(
                    previous.rulebookId(),
                    previous.processingStatus(),
                    List.of());
        }

        // 2. Store file
        RulebookId rulebookId = RulebookId.generate();
        StoredRulebookFile storedFile = fileStorage.store(rulebookId, Arrays.copyOf(command.fileContent(), command.fileContent().length));

        // 3. Save metadata
        String hash = computeHash(command);
        StoredRulebookRegistration registration = new StoredRulebookRegistration(
                rulebookId,
                command.ownerPlayerId(),
                command.operationKey(),
                hash,
                command.format(),
                command.fileContent().length,
                storedFile.key(),
                ProcessingStatus.UPLOADED,
                null, null, null, null,
                0L,
                java.time.Instant.now(),
                java.time.Instant.now(),
                DocumentType.RULEBOOK,
                command.originalFilename());
        registrationRepository.save(registration);

        // 4. Build Rulebook domain object for extraction
        Rulebook rulebook = Rulebook.acceptUpload(
                rulebookId, command.ownerPlayerId(), command.format(),
                new FileSize(command.fileContent().length));

        // 5. Extract content
        byte[] storedContent = fileStorage.read(storedFile);
        ExtractionResult extractionResult = contentExtractor.extract(command.format(), Arrays.copyOf(storedContent, storedContent.length));
        rulebook.recordExtraction(extractionResult);

        // 6. Update registration with extraction results
        StoredRulebookRegistration updated = new StoredRulebookRegistration(
                registration.rulebookId(),
                registration.ownerPlayerId(),
                registration.operationKey(),
                registration.contentHash(),
                registration.format(),
                registration.fileSize(),
                registration.storageKey(),
                rulebook.processingStatus(),
                extractionResult.status(),
                extractionResult.content().orElse(null),
                extractionResult.missingLocations(),
                extractionResult.failure().map(Enum::name).orElse(null),
                registration.version() + 1,
                registration.createdAt(),
                java.time.Instant.now(),
                registration.documentType(),
                registration.originalFilename());
        registrationRepository.save(updated);

        // 7. If eligible for splitting, index
        if (rulebook.isEligibleForSplitting()) {
            try {
                IndexKey indexKey = new IndexKey(rulebookId, hash, "ollama-embedding", "v1");
                IndexingCommand indexingCommand = new IndexingCommand(rulebook, indexKey, embeddingDimension);
                indexingService.indexContent(indexingCommand);
                StoredRulebookRegistration indexed = new StoredRulebookRegistration(
                        updated.rulebookId(),
                        updated.ownerPlayerId(),
                        updated.operationKey(),
                        updated.contentHash(),
                        updated.format(),
                        updated.fileSize(),
                        updated.storageKey(),
                        ProcessingStatus.INDEXED,
                        updated.extractionStatus(),
                        updated.extractedContent(),
                        updated.missingLocations(),
                        updated.failureCode(),
                        updated.version() + 1,
                        updated.createdAt(),
                        java.time.Instant.now(),
                        updated.documentType(),
                        updated.originalFilename());
                registrationRepository.save(indexed);
                return new RulebookProcessingResult(rulebookId, ProcessingStatus.INDEXED, List.of());
            } catch (Exception e) {
                // Indexing failed but extraction succeeded — still return EXTRACTED status
                return new RulebookProcessingResult(
                        rulebookId,
                        ProcessingStatus.EXTRACTED,
                        List.of("indexing failed: " + e.getMessage()));
            }
        }

        return new RulebookProcessingResult(rulebookId, rulebook.processingStatus(), List.of());
    }

    public List<RulebookProcessingResult> processPending() {
        List<StoredRulebookRegistration> pending = registrationRepository.findByOwner(null).stream()
                .filter(r -> r.processingStatus() == ProcessingStatus.UPLOADED || r.processingStatus() == ProcessingStatus.EXTRACTED)
                .toList();
        return pending.stream()
                .map(r -> {
                    try {
                        return process(new UploadRulebookCommand(
                                r.operationKey(),
                                r.ownerPlayerId(),
                                r.format(),
                                fileStorage.read(new StoredRulebookFile(r.storageKey())),
                                r.originalFilename()));
                    } catch (Exception e) {
                        return new RulebookProcessingResult(
                                r.rulebookId(),
                                ProcessingStatus.REJECTED,
                                List.of(e.getMessage()));
                    }
                })
                .toList();
    }

    private static String computeHash(UploadRulebookCommand command) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(command.ownerPlayerId().value().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(command.format().name().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            digest.update((byte) 0);
            return HexFormat.of().formatHex(digest.digest(command.fileContent()));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required", e);
        }
    }
}
