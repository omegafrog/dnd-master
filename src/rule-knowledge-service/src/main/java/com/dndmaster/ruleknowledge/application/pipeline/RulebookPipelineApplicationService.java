package com.dndmaster.ruleknowledge.application.pipeline;

import com.dndmaster.ruleknowledge.application.indexing.IndexingCommand;
import com.dndmaster.ruleknowledge.application.indexing.IndexingFailedException;
import com.dndmaster.ruleknowledge.application.indexing.RulebookIndexingApplicationService;
import com.dndmaster.ruleknowledge.application.registration.RulebookContentExtractor;
import com.dndmaster.ruleknowledge.application.registration.RulebookFileStorage;
import com.dndmaster.ruleknowledge.application.registration.RulebookRegistrationApplicationService;
import com.dndmaster.ruleknowledge.application.registration.RulebookRegistrationRepository;
import com.dndmaster.ruleknowledge.application.registration.SourcePreviewExtractor;
import com.dndmaster.ruleknowledge.application.registration.StoredRulebookFile;
import com.dndmaster.ruleknowledge.application.registration.StoredRulebookRegistration;
import com.dndmaster.ruleknowledge.application.preprocessing.PreprocessingPageState;
import com.dndmaster.ruleknowledge.application.preprocessing.PreprocessingProcessException;
import com.dndmaster.ruleknowledge.application.preprocessing.PreprocessingProcessPort;
import com.dndmaster.ruleknowledge.application.preprocessing.PreprocessingRetryLeaseRepository;
import com.dndmaster.ruleknowledge.application.preprocessing.InMemoryPreprocessingRetryLeaseRepository;
import com.dndmaster.ruleknowledge.application.preprocessing.PreprocessingRetryRequest;
import com.dndmaster.ruleknowledge.application.preprocessing.PreprocessingRunRequest;
import com.dndmaster.ruleknowledge.application.preprocessing.PreprocessingRunResult;
import com.dndmaster.ruleknowledge.application.preprocessing.PreprocessingArtifactImporter;
import com.dndmaster.ruleknowledge.application.publication.RagExtractionPage;
import com.dndmaster.ruleknowledge.application.publication.RagExtractionPublicationRequest;
import com.dndmaster.ruleknowledge.application.publication.RagExtractionPublicationService;
import com.dndmaster.ruleknowledge.application.publication.PublishedRagChunk;
import com.dndmaster.ruleknowledge.domain.index.IndexKey;
import com.dndmaster.ruleknowledge.domain.rulebook.DocumentType;
import com.dndmaster.ruleknowledge.domain.rulebook.ExtractionResult;
import com.dndmaster.ruleknowledge.domain.rulebook.SourcePreviewResult;
import com.dndmaster.ruleknowledge.domain.rulebook.FileSize;
import com.dndmaster.ruleknowledge.domain.rulebook.ProcessingStatus;
import com.dndmaster.ruleknowledge.domain.rulebook.Rulebook;
import com.dndmaster.ruleknowledge.domain.rulebook.RulebookId;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.Duration;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class RulebookPipelineApplicationService implements RulebookUploadProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(RulebookPipelineApplicationService.class);
    private static final UUID CATALOG_OWNER = UUID.fromString("00000000-0000-0000-0000-000000000005");
    private static final Duration PROCESSING_LEASE = Duration.ofMinutes(100);
    private static final int PENDING_BATCH_SIZE = 10;

    private final RulebookRegistrationApplicationService registrationService;
    private final RulebookRegistrationRepository registrationRepository;
    private final RulebookFileStorage fileStorage;
    private final RulebookContentExtractor contentExtractor;
    private final SourcePreviewExtractor sourcePreviewExtractor;
    private final RulebookIndexingApplicationService indexingService;
    private final int embeddingDimension;
    private final PreprocessingProcessPort preprocessingProcessPort;
    private final RagExtractionPublicationService ragExtractionPublicationService;
    private final PreprocessingArtifactImporter preprocessingArtifactImporter;
    private final PreprocessingRetryLeaseRepository retryLeaseRepository;
    private final com.dndmaster.ruleknowledge.application.definition.RulebookActionDefinitionExtractionService actionDefinitionExtractionService;
    private final com.dndmaster.ruleknowledge.application.definition.PublishedRulebookActionSourceRepository actionSourceRepository;

    public RulebookPipelineApplicationService(
            RulebookRegistrationApplicationService registrationService,
            RulebookRegistrationRepository registrationRepository,
            RulebookFileStorage fileStorage,
            RulebookContentExtractor contentExtractor,
            SourcePreviewExtractor sourcePreviewExtractor,
            RulebookIndexingApplicationService indexingService,
            int embeddingDimension) {
        this(registrationService, registrationRepository, fileStorage, contentExtractor, sourcePreviewExtractor,
                indexingService, embeddingDimension, null);
    }

    public RulebookPipelineApplicationService(
            RulebookRegistrationApplicationService registrationService,
            RulebookRegistrationRepository registrationRepository,
            RulebookFileStorage fileStorage,
            RulebookContentExtractor contentExtractor,
            SourcePreviewExtractor sourcePreviewExtractor,
            RulebookIndexingApplicationService indexingService,
            int embeddingDimension,
            PreprocessingProcessPort preprocessingProcessPort) {
        this(registrationService, registrationRepository, fileStorage, contentExtractor, sourcePreviewExtractor,
                indexingService, embeddingDimension, preprocessingProcessPort, null,
                new PreprocessingArtifactImporter(new com.fasterxml.jackson.databind.ObjectMapper()),
                new InMemoryPreprocessingRetryLeaseRepository());
    }

    public RulebookPipelineApplicationService(
            RulebookRegistrationApplicationService registrationService,
            RulebookRegistrationRepository registrationRepository,
            RulebookFileStorage fileStorage,
            RulebookContentExtractor contentExtractor,
            SourcePreviewExtractor sourcePreviewExtractor,
            RulebookIndexingApplicationService indexingService,
            int embeddingDimension,
            PreprocessingProcessPort preprocessingProcessPort,
            RagExtractionPublicationService ragExtractionPublicationService) {
        this(registrationService, registrationRepository, fileStorage, contentExtractor, sourcePreviewExtractor,
                indexingService, embeddingDimension, preprocessingProcessPort, ragExtractionPublicationService,
                new PreprocessingArtifactImporter(new com.fasterxml.jackson.databind.ObjectMapper()),
                new InMemoryPreprocessingRetryLeaseRepository());
    }

    public RulebookPipelineApplicationService(
            RulebookRegistrationApplicationService registrationService,
            RulebookRegistrationRepository registrationRepository,
            RulebookFileStorage fileStorage,
            RulebookContentExtractor contentExtractor,
            SourcePreviewExtractor sourcePreviewExtractor,
            RulebookIndexingApplicationService indexingService,
            int embeddingDimension,
            PreprocessingProcessPort preprocessingProcessPort,
            RagExtractionPublicationService ragExtractionPublicationService,
            PreprocessingArtifactImporter preprocessingArtifactImporter,
            PreprocessingRetryLeaseRepository retryLeaseRepository) {
        this(registrationService, registrationRepository, fileStorage, contentExtractor, sourcePreviewExtractor,
                indexingService, embeddingDimension, preprocessingProcessPort, ragExtractionPublicationService,
                preprocessingArtifactImporter, retryLeaseRepository, null, null);
    }

    public RulebookPipelineApplicationService(
            RulebookRegistrationApplicationService registrationService,
            RulebookRegistrationRepository registrationRepository,
            RulebookFileStorage fileStorage,
            RulebookContentExtractor contentExtractor,
            SourcePreviewExtractor sourcePreviewExtractor,
            RulebookIndexingApplicationService indexingService,
            int embeddingDimension,
            PreprocessingProcessPort preprocessingProcessPort,
            RagExtractionPublicationService ragExtractionPublicationService,
            PreprocessingArtifactImporter preprocessingArtifactImporter,
            PreprocessingRetryLeaseRepository retryLeaseRepository,
            com.dndmaster.ruleknowledge.application.definition.RulebookActionDefinitionExtractionService actionDefinitionExtractionService,
            com.dndmaster.ruleknowledge.application.definition.PublishedRulebookActionSourceRepository actionSourceRepository) {
        this.registrationService = Objects.requireNonNull(registrationService, "registrationService must not be null");
        this.registrationRepository = Objects.requireNonNull(registrationRepository, "registrationRepository must not be null");
        this.fileStorage = Objects.requireNonNull(fileStorage, "fileStorage must not be null");
        this.contentExtractor = Objects.requireNonNull(contentExtractor, "contentExtractor must not be null");
        this.sourcePreviewExtractor = Objects.requireNonNull(sourcePreviewExtractor, "sourcePreviewExtractor must not be null");
        this.indexingService = Objects.requireNonNull(indexingService, "indexingService must not be null");
        if (embeddingDimension <= 0) {
            throw new IllegalArgumentException("embeddingDimension must be positive");
        }
        this.embeddingDimension = embeddingDimension;
        this.preprocessingProcessPort = preprocessingProcessPort;
        this.ragExtractionPublicationService = ragExtractionPublicationService;
        this.preprocessingArtifactImporter = Objects.requireNonNull(preprocessingArtifactImporter, "artifact importer must not be null");
        this.retryLeaseRepository = Objects.requireNonNull(retryLeaseRepository, "retry lease repository must not be null");
        this.actionDefinitionExtractionService = actionDefinitionExtractionService;
        this.actionSourceRepository = actionSourceRepository;
    }

    @Override
    public RulebookProcessingResult process(UploadRulebookCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        String contentHash = computeHash(command);

        Optional<StoredRulebookRegistration> existing = registrationRepository.findByOperationKey(command.operationKey());
        if (existing.isPresent()) {
            StoredRulebookRegistration previous = existing.get();
            if (!previous.contentHash().equals(contentHash)
                    || !previous.ownerPlayerId().equals(command.ownerPlayerId())
                    || previous.documentType() != command.documentType()
                    || previous.format() != command.format()) {
                throw new RulebookPipelineException("conflict: same idempotency key with different request");
            }
            return new RulebookProcessingResult(previous.rulebookId(), previous.processingStatus(), List.of());
        }

        Optional<StoredRulebookRegistration> duplicate = registrationRepository.findByOwnerAndContentHash(
                command.ownerPlayerId(), contentHash);
        if (duplicate.isPresent()) {
            StoredRulebookRegistration previous = duplicate.get();
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
                command.originalFilename(), command.extractionActorPlayerId());
        try {
            registrationRepository.save(queued);
        } catch (com.dndmaster.ruleknowledge.application.registration.RulebookRegistrationConflictException collision) {
            Optional<StoredRulebookRegistration> concurrent = registrationRepository.findByOperationKey(command.operationKey());
            if (concurrent.isPresent()) {
                StoredRulebookRegistration previous = concurrent.get();
                if (!previous.contentHash().equals(contentHash)
                        || !previous.ownerPlayerId().equals(command.ownerPlayerId())
                        || previous.documentType() != command.documentType()
                        || previous.format() != command.format()) {
                    throw new RulebookPipelineException("conflict: same idempotency key with different request", collision);
                }
                return new RulebookProcessingResult(previous.rulebookId(), previous.processingStatus(), List.of());
            }
            Optional<StoredRulebookRegistration> duplicateAfterRace = registrationRepository.findByOwnerAndContentHash(
                    command.ownerPlayerId(), contentHash);
            if (duplicateAfterRace.isPresent()) {
                StoredRulebookRegistration previous = duplicateAfterRace.get();
                return new RulebookProcessingResult(previous.rulebookId(), previous.processingStatus(), List.of());
            }
            throw new RulebookPipelineException("conflict: rulebook registration uniqueness violation", collision);
        }
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
        StoredRulebookRegistration queued = withStatus(registration, ProcessingStatus.QUEUED, null, null, null, null, null);
        registrationRepository.save(queued);
        return new RulebookProcessingResult(rulebookId, ProcessingStatus.QUEUED, List.of());
    }

    public RulebookProcessingResult retryPages(RulebookId rulebookId, String requestId, List<Integer> pages) {
        return retryPages(rulebookId, requestId, pages, Map.of());
    }

    public RulebookProcessingResult retryPages(RulebookId rulebookId, String requestId, List<Integer> pages,
                                               Map<Integer, Map<String, Integer>> layoutSelections) {
        return retryPages(rulebookId, requestId, pages, layoutSelections, null, null);
    }

    public RulebookProcessingResult retryPages(RulebookId rulebookId, String requestId, List<Integer> pages,
                                               Map<Integer, Map<String, Integer>> layoutSelections,
                                               String candidateVersion, UUID confirmedBy) {
        Objects.requireNonNull(rulebookId, "rulebook id must not be null");
        if (requestId == null || requestId.isBlank()) throw new IllegalArgumentException("retry request id must not be blank");
        if (pages == null || pages.isEmpty() || pages.stream().anyMatch(page -> page == null || page < 1)) {
            throw new IllegalArgumentException("retry pages must contain positive page numbers");
        }
        StoredRulebookRegistration registration = registrationRepository.findById(rulebookId)
                .orElseThrow(() -> new IllegalArgumentException("knowledge document not found"));
        if (registration.processingStatus() == ProcessingStatus.INDEXED
                && retryLeaseRepository.completedResult(rulebookId, requestId).isPresent()) {
            return new RulebookProcessingResult(rulebookId, ProcessingStatus.INDEXED, List.of());
        }
        if (registration.processingStatus() != ProcessingStatus.NEEDS_REVIEW
                || registration.candidateExtractionVersion() == null) {
            throw new IllegalStateException("only review candidate can retry pages");
        }
        if (candidateVersion != null && !candidateVersion.equals(registration.candidateExtractionVersion())) {
            throw new IllegalStateException("review candidate version changed");
        }
        List<Integer> selected = pages.stream().distinct().sorted().toList();
        List<Integer> retryable = registration.preprocessingPages().stream()
                .filter(page -> "NEEDS_REVIEW".equals(page.status()) && page.attempts() < 3)
                .map(PreprocessingPageState::pageNumber).toList();
        if (!retryable.containsAll(selected)) throw new IllegalStateException("selected pages are not retryable");
        if (layoutSelections == null) layoutSelections = Map.of();
        if (!selected.containsAll(layoutSelections.keySet()) || (!layoutSelections.isEmpty() && confirmedBy == null && candidateVersion != null)) {
            throw new IllegalArgumentException("invalid confirmed page selection");
        }
        for (var entry : layoutSelections.entrySet()) {
            PreprocessingPageState page = registration.preprocessingPages().stream()
                    .filter(item -> item.pageNumber() == entry.getKey()).findFirst().orElseThrow();
            if (page.layoutReview() == null || entry.getValue() == null || entry.getValue().isEmpty()
                    || (confirmedBy != null && !page.layoutReview().regions().stream()
                            .map(PreprocessingPageState.LayoutRegionReview::regionId)
                            .collect(java.util.stream.Collectors.toSet()).equals(entry.getValue().keySet()))) {
                throw new IllegalArgumentException("invalid layout region selection");
            }
            for (var selection : entry.getValue().entrySet()) {
                if (selection.getValue() == null || page.layoutReview().regions().stream()
                        .filter(region -> region.regionId().equals(selection.getKey()))
                        .noneMatch(region -> region.candidates().stream()
                                .anyMatch(candidate -> candidate.candidateIndex() == selection.getValue()))) {
                    throw new IllegalArgumentException("invalid layout candidate selection");
                }
            }
        }
        if (confirmedBy != null && !Files.isDirectory(preprocessingCandidateRoot(rulebookId,
                registration.candidateExtractionVersion()))) {
            throw new IllegalStateException("review candidate artifacts are unavailable");
        }
        if (preprocessingProcessPort == null) throw new IllegalStateException("preprocessing retry is not configured");
        var claim = retryLeaseRepository.claim(rulebookId, requestId, registration.candidateExtractionVersion(), selected, Duration.ofMinutes(10));
        if (claim.completed()) return new RulebookProcessingResult(rulebookId, ProcessingStatus.INDEXED, List.of());
        if (!claim.acquired()) throw new IllegalStateException("retry is already leased");
        long expectedVersion = registration.version();
        StoredRulebookRegistration processing = withRetryStatus(registration, ProcessingStatus.PROCESSING, null);
        registrationRepository.save(processing);
        try {
            Path workingRoot = preprocessingWorkingRoot(rulebookId);
            try {
                Files.createDirectories(workingRoot);
                Files.write(workingRoot.resolve("source.pdf"), fileStorage.read(new StoredRulebookFile(registration.storageKey())));
            } catch (java.io.IOException exception) {
                throw new PreprocessingProcessException("PREPROCESSING_ARTIFACT_UNAVAILABLE", exception);
            }
            Path artifactRoot = preprocessingRoot(rulebookId);
            Path candidateDirectory = preprocessingCandidateRoot(rulebookId, registration.candidateExtractionVersion());
            boolean rebuildMissingCandidate = !Files.isDirectory(candidateDirectory);
            PreprocessingRunResult result;
            if (rebuildMissingCandidate) {
                // A previous process may have persisted the review metadata
                // without retaining its candidate files (for example after a
                // temporary-directory cleanup). Recreate a fresh candidate
                // instead of turning a valid user retry into an opaque 500.
                result = preprocessingProcessPort.preprocess(new PreprocessingRunRequest(
                        registration.operationKey(), workingRoot.resolve("source.pdf"), registration.contentHash(),
                        "rag-preprocessing-v2", artifactRoot, null, preprocessingActor(registration)));
                validatePreprocessingResult(registration, result);
            } else {
                result = preprocessingProcessPort.retryPages(new PreprocessingRetryRequest(
                        requestId, registration.candidateExtractionVersion(), artifactRoot, selected, layoutSelections,
                        confirmedBy == null ? null : confirmedBy.toString(), preprocessingActor(registration)));
                validateRetryResult(registration, requestId, selected, result);
            }
            StoredRulebookRegistration latest = registrationRepository.findById(rulebookId).orElseThrow();
            if (latest.version() != processing.version() || latest.version() < expectedVersion) {
                retryLeaseRepository.release(rulebookId, requestId, claim.leaseToken());
                return new RulebookProcessingResult(rulebookId, latest.processingStatus(), List.of("STALE_RETRY_RESPONSE"));
            }
            if ("NEEDS_REVIEW".equals(result.status())) {
                StoredRulebookRegistration review = withPreprocessing(latest, ProcessingStatus.NEEDS_REVIEW,
                        "PREPROCESSING_NEEDS_REVIEW", result,
                        sourcePreviewExtractor.preview(registration.format(), fileStorage.read(new StoredRulebookFile(registration.storageKey()))));
                registrationRepository.save(review);
                retryLeaseRepository.release(rulebookId, requestId, claim.leaseToken());
                return new RulebookProcessingResult(rulebookId, ProcessingStatus.NEEDS_REVIEW, diagnosticWarnings(result.pages()));
            }
            if (!"READY".equals(result.status())) throw new PreprocessingProcessException("INVALID_PREPROCESSING_STATUS");
            publishPreprocessed(latest, result);
            logActionDefinitionExtractionDeferred(latest, result, "retry_pages_published");
            StoredRulebookRegistration indexed = withPreprocessing(latest, ProcessingStatus.INDEXED, null, result,
                    sourcePreviewExtractor.preview(registration.format(), fileStorage.read(new StoredRulebookFile(registration.storageKey()))));
            registrationRepository.save(indexed);
            if (!retryLeaseRepository.complete(rulebookId, requestId, claim.leaseToken(), registration.candidateExtractionVersion(), result.versionId())) {
                retryLeaseRepository.release(rulebookId, requestId, claim.leaseToken());
                return new RulebookProcessingResult(rulebookId, ProcessingStatus.INDEXED, List.of("STALE_RETRY_RESPONSE"));
            }
            return new RulebookProcessingResult(rulebookId, ProcessingStatus.INDEXED, List.of());
        } catch (RuntimeException exception) {
            retryLeaseRepository.release(rulebookId, requestId, claim.leaseToken());
            StoredRulebookRegistration current = registrationRepository.findById(rulebookId).orElse(registration);
            if (current.version() == processing.version()) {
                registrationRepository.save(withRetryStatus(current, ProcessingStatus.NEEDS_REVIEW, "PREPROCESSING_RETRY_FAILED"));
            }
            if (exception instanceof IllegalStateException illegal && "retry is already leased".equals(illegal.getMessage())) throw illegal;
            throw exception;
        }
    }

    public void delete(RulebookId rulebookId, com.dndmaster.ruleknowledge.domain.rulebook.OwnerPlayerId owner) {
        StoredRulebookRegistration registration = registrationRepository.findById(rulebookId)
                .orElseThrow(() -> new IllegalArgumentException("knowledge document not found"));
        if (!registration.ownerPlayerId().equals(owner)) {
            throw new SecurityException("knowledge document does not belong to authenticated player");
        }
        registrationRepository.deleteById(rulebookId);
        fileStorage.delete(new StoredRulebookFile(registration.storageKey()));
    }

    public RulebookProcessingResult retryActionDefinitionExtraction(RulebookId rulebookId,
            java.util.UUID extractionActorPlayerId) {
        StoredRulebookRegistration registration = registrationRepository.findById(rulebookId)
                .orElseThrow(() -> new IllegalArgumentException("knowledge document not found"));
        if (registration.documentType() != DocumentType.RULEBOOK || !registration.ownerPlayerId().value().equals(CATALOG_OWNER))
            throw new SecurityException("action definition extraction is limited to shared rulebooks");
        if (actionDefinitionExtractionService == null || actionSourceRepository == null)
            throw new IllegalStateException("action definition extraction is not configured");
        var source = actionSourceRepository.findCurrent(rulebookId.value())
                .orElseThrow(() -> new IllegalStateException("published indexed source is unavailable"));
        var extraction = actionDefinitionExtractionService.extract(source.documentId(), source.extractionVersion(),
                source.scopeVersion(), source.preprocessedPages(), extractionActorPlayerId);
        if (!"COMPLETED".equals(extraction.status())) throw new IllegalStateException("action definition extraction did not complete");
        if (registration.processingStatus() != ProcessingStatus.INDEXED
                && registration.processingStatus() != ProcessingStatus.INDEXED_WITH_WARNINGS) {
            registrationRepository.save(withStatus(registration, ProcessingStatus.INDEXED, null, null, null, null, null));
        }
        return new RulebookProcessingResult(rulebookId, ProcessingStatus.INDEXED, List.of());
    }

    private static void logActionDefinitionExtractionDeferred(StoredRulebookRegistration registration,
            PreprocessingRunResult result, String publicationStage) {
        if (!registration.ownerPlayerId().value().equals(CATALOG_OWNER)
                || registration.documentType() != DocumentType.RULEBOOK) return;
        log("dev_rulebook_action_extraction stage=automatic_extraction_deferred documentId={} extractionVersion={} pageCount={} publicationStage={} recoveryAction=extract_selected_definitions_when_needed outcome=deferred",
                registration.rulebookId().value(), result.versionId(), result.pages().size(), publicationStage);
    }

    private RulebookProcessingResult processClaimedRegistration(StoredRulebookRegistration registration) {
        if (registration.processingStatus() != ProcessingStatus.PROCESSING) {
            throw new IllegalStateException("only claimed document can be processed");
        }
        if (registration.format() == com.dndmaster.ruleknowledge.domain.rulebook.RulebookFormat.PDF) {
            if (preprocessingProcessPort == null) {
                return failPreprocessing(registration, "PREPROCESSING_PORT_REQUIRED");
            }
            return processWithPreprocessing(registration);
        }
        if (preprocessingProcessPort != null
                && registration.format() != com.dndmaster.ruleknowledge.domain.rulebook.RulebookFormat.IMAGE) {
            return rejectUnsupportedFormat(registration);
        }
        ExtractionResult extractionResult = null;
        SourcePreviewResult previewResult = null;
        try {
            byte[] storedContent = fileStorage.read(new StoredRulebookFile(registration.storageKey()));
            byte[] safeStoredContent = Arrays.copyOf(storedContent, storedContent.length);
            previewResult = sourcePreviewExtractor.preview(registration.format(), safeStoredContent);
            extractionResult = contentExtractor.extract(registration.format(), safeStoredContent);
            Rulebook rulebook = Rulebook.acceptUpload(
                    registration.rulebookId(),
                    registration.ownerPlayerId(),
                    registration.format(),
                    new FileSize(registration.fileSize()));
            rulebook.recordExtraction(extractionResult);
            if (!rulebook.isEligibleForSplitting()) {
                if (rulebook.processingStatus() == ProcessingStatus.NEEDS_INPUT) {
                    StoredRulebookRegistration needsInput = withStatus(
                            registration,
                            ProcessingStatus.NEEDS_INPUT,
                            extractionResult,
                            previewResult,
                            describeExtractionFailure(extractionResult),
                            extractionResult.content().orElse(null),
                            extractionResult.missingLocations());
                    registrationRepository.save(needsInput);
                    return new RulebookProcessingResult(registration.rulebookId(), ProcessingStatus.NEEDS_INPUT, List.of(
                            describeExtractionFailure(extractionResult)));
                }
                return fail(registration, extractionResult, previewResult, describeExtractionFailure(extractionResult));
            }
            attemptIndexing(indexableRulebook(rulebook, previewResult), registration);
            StoredRulebookRegistration indexed = withStatus(
                    registration,
                    ProcessingStatus.INDEXED,
                    extractionResult,
                    previewResult,
                    null,
                    extractionResult.content().orElse(null),
                    extractionResult.missingLocations());
            registrationRepository.save(indexed);
            return new RulebookProcessingResult(registration.rulebookId(), ProcessingStatus.INDEXED, List.of());
        } catch (IndexingFailedException exception) {
            return fail(registration, extractionResult, previewResult, describeFailure(exception));
        } catch (RuntimeException exception) {
            return fail(registration, extractionResult, previewResult, describeFailure(exception));
        }
    }

    private RulebookProcessingResult processWithPreprocessing(StoredRulebookRegistration registration) {
        long startedAt = System.nanoTime();
        try {
            byte[] storedContent = fileStorage.read(new StoredRulebookFile(registration.storageKey()));
            SourcePreviewResult previewResult = sourcePreviewExtractor.preview(registration.format(), storedContent);
            Path runRoot = preprocessingWorkingRoot(registration.rulebookId());
            Files.createDirectories(runRoot);
            Path source = runRoot.resolve("source.pdf");
            Path output = runRoot.resolve("artifacts");
            Files.createDirectories(output);
            Files.write(source, storedContent);
            PreprocessingRunResult result = preprocessingProcessPort.preprocess(new PreprocessingRunRequest(
                    registration.operationKey(), source, registration.contentHash(), "rag-preprocessing-v2", output, null,
                    preprocessingActor(registration)));
            validatePreprocessingResult(registration, result);
            log("dev_preprocessing_publication stage=page_result_validation documentId={} expectedPageCount={} actualPageCount={} searchablePageCount={} warningPageCount={} unreadablePageCount={} status={} outcome=validated",
                    registration.rulebookId().value(), result.pages().size(), result.pages().size(),
                    result.pages().stream().filter(page -> Set.of("VALIDATED", "USABLE_WITH_WARNINGS").contains(page.status())).count(),
                    result.pages().stream().filter(page -> "USABLE_WITH_WARNINGS".equals(page.status())).count(),
                    result.pages().stream().filter(page -> "NEEDS_REVIEW".equals(page.status())).count(), result.status());
            if ("NEEDS_REVIEW".equals(result.status())) {
                StoredRulebookRegistration review = withPreprocessing(
                        registration, ProcessingStatus.NEEDS_REVIEW, "PREPROCESSING_NEEDS_REVIEW", result, previewResult);
                registrationRepository.save(review);
                return new RulebookProcessingResult(
                        registration.rulebookId(), ProcessingStatus.NEEDS_REVIEW, diagnosticWarnings(result.pages()));
            }
            if ("PARTIAL".equals(result.status())) {
                if (ragExtractionPublicationService == null) {
                    log("dev_preprocessing_publication stage=partial_not_published documentId={} searchablePageCount={} unreadablePageCount={} failureKind=rag_publication_unavailable recoveryAction=retain_source_and_review_pages elapsedMs={} outcome=needs_review",
                            registration.rulebookId().value(),
                            result.pages().stream().filter(page -> Set.of("VALIDATED", "USABLE_WITH_WARNINGS").contains(page.status())).count(),
                            result.pages().stream().filter(page -> "NEEDS_REVIEW".equals(page.status())).count(),
                            elapsedMillis(startedAt));
                    StoredRulebookRegistration review = withPreprocessing(registration, ProcessingStatus.NEEDS_REVIEW,
                            "PARTIAL_CONTENT_NOT_INDEXED", result, previewResult);
                    registrationRepository.save(review);
                    return new RulebookProcessingResult(registration.rulebookId(), ProcessingStatus.NEEDS_REVIEW,
                            diagnosticWarnings(result.pages()));
                }
                publishPreprocessed(registration, result);
                logActionDefinitionExtractionDeferred(registration, result, "partial_pages_published");
                StoredRulebookRegistration indexedWithWarnings = withPreprocessing(registration,
                        ProcessingStatus.INDEXED_WITH_WARNINGS, null, result, previewResult);
                registrationRepository.save(indexedWithWarnings);
                log("dev_preprocessing_publication stage=partial_published documentId={} expectedPageCount={} searchablePageCount={} warningPageCount={} unreadablePageCount={} route=publish_searchable_pages elapsedMs={} outcome=indexed_with_warnings",
                        registration.rulebookId().value(), result.pages().size(),
                        result.pages().stream().filter(page -> Set.of("VALIDATED", "USABLE_WITH_WARNINGS").contains(page.status())).count(),
                        result.pages().stream().filter(page -> "USABLE_WITH_WARNINGS".equals(page.status())).count(),
                        result.pages().stream().filter(page -> "NEEDS_REVIEW".equals(page.status())).count(),
                        elapsedMillis(startedAt));
                return new RulebookProcessingResult(registration.rulebookId(), ProcessingStatus.INDEXED_WITH_WARNINGS,
                        diagnosticWarnings(result.pages()));
            }
            if ("READY".equals(result.status())) {
                publishPreprocessed(registration, result);
                logActionDefinitionExtractionDeferred(registration, result, "pages_published");
                ProcessingStatus nextStatus = ragExtractionPublicationService == null
                        ? ProcessingStatus.VALIDATED : ProcessingStatus.INDEXED;
                StoredRulebookRegistration validated = withPreprocessing(
                        registration, nextStatus, null, result, previewResult);
                registrationRepository.save(validated);
                return new RulebookProcessingResult(registration.rulebookId(), nextStatus, List.of());
            }
            throw new PreprocessingProcessException("INVALID_PREPROCESSING_STATUS");
        } catch (PreprocessingProcessException exception) {
            log("dev_preprocessing_publication stage=failed documentId={} failureKind={} elapsedMs={} recoveryAction=retain_source_and_page_diagnostics outcome=failed",
                    registration.rulebookId().value(), exception.code(), elapsedMillis(startedAt));
            return failPreprocessing(registration, exception.code());
        } catch (RuntimeException exception) {
            log("dev_preprocessing_publication stage=failed documentId={} failureKind={} elapsedMs={} recoveryAction=retain_source_and_page_diagnostics outcome=failed",
                    registration.rulebookId().value(), exception.getClass().getSimpleName(), elapsedMillis(startedAt));
            return failPreprocessing(registration, "PREPROCESSING_FAILED");
        } catch (Exception exception) {
            log("dev_preprocessing_publication stage=failed documentId={} failureKind={} elapsedMs={} recoveryAction=retain_source_and_page_diagnostics outcome=failed",
                    registration.rulebookId().value(), exception.getClass().getSimpleName(), elapsedMillis(startedAt));
            return failPreprocessing(registration, "PREPROCESSING_FAILED");
        }
    }

    private static void log(String pattern, Object... arguments) {
        if (Boolean.parseBoolean(System.getProperty("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED",
                System.getenv("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED")))) LOGGER.info(pattern, arguments);
    }

    private static long elapsedMillis(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
    }

    private UUID preprocessingActor(StoredRulebookRegistration registration) {
        UUID actor = registration.extractionActorPlayerId();
        log("dev_preprocessing_actor stage=identity_selection documentId={} actorSource={} outcome=selected",
                registration.rulebookId().value(), actor == null ? "owner_fallback" : "stored_extraction_actor");
        return actor == null ? registration.ownerPlayerId().value() : actor;
    }

    private RulebookProcessingResult rejectUnsupportedFormat(StoredRulebookRegistration registration) {
        StoredRulebookRegistration rejected = withStatus(
                registration, ProcessingStatus.REJECTED, null, null, "UNSUPPORTED_FORMAT", null, List.of());
        registrationRepository.save(rejected);
        return new RulebookProcessingResult(registration.rulebookId(), ProcessingStatus.REJECTED, List.of("UNSUPPORTED_FORMAT"));
    }

    private RulebookProcessingResult failPreprocessing(StoredRulebookRegistration registration, String code) {
        StoredRulebookRegistration failed = withStatus(
                registration, ProcessingStatus.FAILED, null, null, code, null, List.of());
        registrationRepository.save(failed);
        return new RulebookProcessingResult(registration.rulebookId(), ProcessingStatus.FAILED, List.of(code));
    }

    private static void validatePreprocessingResult(StoredRulebookRegistration registration, PreprocessingRunResult result) {
        if (!registration.operationKey().equals(result.requestId())) {
            throw new PreprocessingProcessException("CORRELATION_ID_MISMATCH");
        }
        if (!registration.contentHash().equals(result.sourceSha256())) {
            throw new PreprocessingProcessException("SOURCE_HASH_MISMATCH");
        }
        if (!"rag-preprocessing-v2".equals(result.policyVersion())) {
            throw new PreprocessingProcessException("POLICY_VERSION_MISMATCH");
        }
        if (result.pages().isEmpty()) {
            throw new PreprocessingProcessException("PAGE_MANIFEST_MISMATCH");
        }
        for (int index = 0; index < result.pages().size(); index++) {
            if (result.pages().get(index).pageNumber() != index + 1) {
                throw new PreprocessingProcessException("PAGE_MANIFEST_MISMATCH");
            }
        }
        if ("READY".equals(result.status())
                && result.pages().stream().anyMatch(page -> !Set.of("VALIDATED", "EXCLUDED_FORM").contains(page.status()))) {
            throw new PreprocessingProcessException("UNVALIDATED_PAGE");
        }
        long searchablePages = result.pages().stream()
                .filter(page -> Set.of("VALIDATED", "USABLE_WITH_WARNINGS").contains(page.status())).count();
        long uncertainPages = result.pages().stream()
                .filter(page -> Set.of("USABLE_WITH_WARNINGS", "NEEDS_REVIEW").contains(page.status())).count();
        if ("PARTIAL".equals(result.status()) && (searchablePages == 0 || uncertainPages == 0)) {
            throw new PreprocessingProcessException("PAGE_MANIFEST_MISMATCH");
        }
        if ("NEEDS_REVIEW".equals(result.status()) && (searchablePages != 0
                || result.pages().stream().noneMatch(page -> "NEEDS_REVIEW".equals(page.status())))) {
            throw new PreprocessingProcessException("PAGE_MANIFEST_MISMATCH");
        }
        if (!"READY".equals(result.status()) && !"PARTIAL".equals(result.status())
                && !"NEEDS_REVIEW".equals(result.status())) {
            throw new PreprocessingProcessException("INVALID_PREPROCESSING_STATUS");
        }
    }

    private static List<String> diagnosticWarnings(List<PreprocessingPageState> pages) {
        return pages.stream()
                .filter(page -> "USABLE_WITH_WARNINGS".equals(page.status()) || "NEEDS_REVIEW".equals(page.status()))
                .flatMap(page -> page.findings().stream())
                .distinct()
                .toList();
    }

    private void publishPreprocessed(StoredRulebookRegistration registration, PreprocessingRunResult result) {
        if (ragExtractionPublicationService == null) return;
        List<PublishedRagChunk> chunks = preprocessingArtifactImporter.readChunks(result);
        List<RagExtractionPage> pages = result.pages().stream()
                .map(page -> new RagExtractionPage(page.pageNumber(), page.status(), page.attempts(), page.findings(),
                        page.pageClassification(), page.formDiagnostics()))
                .toList();
        RagExtractionPublicationRequest request = new RagExtractionPublicationRequest(
                registration.rulebookId(), registration.ownerPlayerId(), result.requestId(), result.versionId(),
                registration.contentHash(), result.policyVersion(), result.artifacts().manifestSha256(), pages, chunks);
        ragExtractionPublicationService.publish(request);
    }

    private static Path preprocessingRoot(RulebookId rulebookId) {
        return preprocessingWorkingRoot(rulebookId).resolve("artifacts")
                .toAbsolutePath().normalize();
    }

    private static Path preprocessingCandidateRoot(RulebookId rulebookId, String version) {
        return preprocessingRoot(rulebookId).resolve("versions").resolve(version);
    }

    private static Path preprocessingWorkingRoot(RulebookId rulebookId) {
        return Path.of(System.getProperty("java.io.tmpdir"), "dnd-rag-preprocessing", rulebookId.value().toString())
                .toAbsolutePath().normalize();
    }

    private static void validateRetryResult(
            StoredRulebookRegistration registration, String requestId, List<Integer> selected, PreprocessingRunResult result) {
        if (!requestId.equals(result.requestId())) throw new PreprocessingProcessException("CORRELATION_ID_MISMATCH");
        if (!registration.contentHash().equals(result.sourceSha256())) throw new PreprocessingProcessException("SOURCE_HASH_MISMATCH");
        if (!"rag-preprocessing-v2".equals(result.policyVersion())) throw new PreprocessingProcessException("POLICY_VERSION_MISMATCH");
        if (result.pages().isEmpty()) throw new PreprocessingProcessException("PAGE_MANIFEST_MISMATCH");
        // A page retry may legitimately remain quarantined as NEEDS_REVIEW. In
        // that case the preprocessing process keeps the candidate version;
        // only a READY promotion must produce a new version.
        if ("READY".equals(result.status()) && registration.candidateExtractionVersion().equals(result.versionId())) {
            throw new PreprocessingProcessException("RETRY_VERSION_NOT_ADVANCED");
        }
        if (result.pages().size() != registration.preprocessingPages().size()) {
            throw new PreprocessingProcessException("PAGE_MANIFEST_MISMATCH");
        }
        for (int index = 0; index < result.pages().size(); index++) {
            PreprocessingPageState before = registration.preprocessingPages().get(index);
            PreprocessingPageState after = result.pages().get(index);
            if (before.pageNumber() != after.pageNumber()) {
                throw new PreprocessingProcessException("PAGE_MANIFEST_MISMATCH");
            }
            if (selected.contains(before.pageNumber())) {
                if (after.attempts() <= before.attempts() || after.attempts() > 3) {
                    throw new PreprocessingProcessException("RETRY_ATTEMPT_MISMATCH");
                }
            } else if (!before.equals(after)) {
                throw new PreprocessingProcessException("UNSELECTED_PAGE_CHANGED");
            }
        }
        if (!"READY".equals(result.status()) && !"NEEDS_REVIEW".equals(result.status())) {
            throw new PreprocessingProcessException("INVALID_PREPROCESSING_STATUS");
        }
        if ("READY".equals(result.status()) && result.pages().stream()
                .anyMatch(page -> !Set.of("VALIDATED", "EXCLUDED_FORM").contains(page.status()))) {
            throw new PreprocessingProcessException("UNVALIDATED_PAGE");
        }
    }

    private static StoredRulebookRegistration withRetryStatus(
            StoredRulebookRegistration registration, ProcessingStatus status, String failureCode) {
        return new StoredRulebookRegistration(
                registration.rulebookId(), registration.ownerPlayerId(), registration.operationKey(), registration.contentHash(),
                registration.format(), registration.fileSize(), registration.storageKey(), status, registration.extractionStatus(),
                registration.extractedContent(), registration.missingLocations(), failureCode, registration.version() + 1,
                registration.createdAt(), Instant.now(), registration.documentType(), registration.originalFilename(),
                registration.previewContent(), registration.previewWarnings(), registration.previewSpans(), registration.previewAssets(),
                registration.preprocessingOperationId(), registration.candidateExtractionVersion(), registration.preprocessingPolicyVersion(),
                registration.preprocessingManifestSha256(), registration.preprocessingPages(), registration.extractionActorPlayerId());
    }

    private void attemptIndexing(Rulebook rulebook, StoredRulebookRegistration registration) {
        IndexKey indexKey = new IndexKey(
                registration.rulebookId(), registration.contentHash(), "ollama-embedding", "v1-" + registration.contentHash());
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

    /**
     * PDF text extraction and source preview use different layout-aware readers.
     * The preview reader preserves page reading order better, so use it as the
     * vector-index input when available while keeping the canonical extraction
     * result persisted for the document lifecycle.
     */
    private static Rulebook indexableRulebook(Rulebook extracted, SourcePreviewResult preview) {
        if (preview == null || preview.content() == null || preview.content().isBlank()) return extracted;
        Rulebook indexed = Rulebook.acceptUpload(
                extracted.id(), extracted.ownerPlayerId(), extracted.format(), extracted.fileSize());
        indexed.recordExtraction(ExtractionResult.success(preview.content()));
        return indexed;
    }

    private RulebookProcessingResult fail(
            StoredRulebookRegistration registration, ExtractionResult extractionResult, SourcePreviewResult previewResult, String reason) {
        StoredRulebookRegistration failed = withStatus(
                registration,
                ProcessingStatus.FAILED,
                extractionResult,
                previewResult,
                reason,
                extractionResult != null ? extractionResult.content().orElse(null) : registration.extractedContent(),
                extractionResult != null ? extractionResult.missingLocations() : registration.missingLocations());
        registrationRepository.save(failed);
        return new RulebookProcessingResult(registration.rulebookId(), ProcessingStatus.FAILED, List.of(reason));
    }

    private static String computeHash(UploadRulebookCommand command) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
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
            SourcePreviewResult previewResult,
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
                registration.originalFilename(),
                previewResult != null && previewResult.content() != null ? previewResult.content() : registration.previewContent(),
                previewResult != null ? previewResult.warnings() : registration.previewWarnings(),
                previewResult != null ? previewResult.spans() : registration.previewSpans(),
                previewResult != null ? previewResult.assets() : registration.previewAssets(),
                registration.preprocessingOperationId(),
                registration.candidateExtractionVersion(),
                registration.preprocessingPolicyVersion(),
                registration.preprocessingManifestSha256(),
                registration.preprocessingPages(), registration.extractionActorPlayerId());
    }

    private static StoredRulebookRegistration withPreprocessing(
            StoredRulebookRegistration registration,
            ProcessingStatus status,
            String failureCode,
            PreprocessingRunResult result,
            SourcePreviewResult previewResult) {
        return new StoredRulebookRegistration(
                registration.rulebookId(),
                registration.ownerPlayerId(),
                registration.operationKey(),
                registration.contentHash(),
                registration.format(),
                registration.fileSize(),
                registration.storageKey(),
                status,
                registration.extractionStatus(),
                registration.extractedContent(),
                registration.missingLocations(),
                failureCode,
                registration.version() + 1,
                registration.createdAt(),
                Instant.now(),
                registration.documentType(),
                registration.originalFilename(),
                previewResult != null ? previewResult.content() : registration.previewContent(),
                previewResult != null ? previewResult.warnings() : registration.previewWarnings(),
                previewResult != null ? previewResult.spans() : registration.previewSpans(),
                previewResult != null ? previewResult.assets() : registration.previewAssets(),
                result.requestId(),
                result.versionId(),
                result.policyVersion(),
                result.artifacts().manifestSha256(),
                result.pages(),
                registration.extractionActorPlayerId());
    }
}
