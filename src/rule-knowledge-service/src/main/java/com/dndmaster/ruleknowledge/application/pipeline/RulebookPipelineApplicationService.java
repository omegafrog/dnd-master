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
import com.dndmaster.ruleknowledge.application.evidence.RuleEvidenceProjectionApplicationService;
import com.dndmaster.ruleknowledge.application.extraction.DocumentExtractionPort;
import com.dndmaster.ruleknowledge.application.extraction.NormalizedDocumentExtractionPort;
import com.dndmaster.ruleknowledge.domain.document.evidence.StructuralEvidenceExtractor;
import com.dndmaster.ruleknowledge.domain.document.anchor.AnchorSkeletonResolver;
import com.dndmaster.ruleknowledge.domain.document.hierarchy.CanonicalHierarchyResolver;
import com.dndmaster.ruleknowledge.domain.document.hierarchy.HierarchyMetrics;
import com.dndmaster.ruleknowledge.domain.document.hierarchy.CanonicalCutoverPolicy;
import com.dndmaster.ruleknowledge.domain.index.IndexKey;
import com.dndmaster.ruleknowledge.domain.rulebook.DocumentType;
import com.dndmaster.ruleknowledge.domain.rulebook.ExtractionResult;
import com.dndmaster.ruleknowledge.domain.rulebook.SourcePreviewResult;
import com.dndmaster.ruleknowledge.domain.rulebook.FileSize;
import com.dndmaster.ruleknowledge.domain.rulebook.ProcessingStatus;
import com.dndmaster.ruleknowledge.domain.rulebook.Rulebook;
import com.dndmaster.ruleknowledge.domain.rulebook.RulebookId;
import com.dndmaster.ruleknowledge.domain.extraction.DocumentNode;
import com.dndmaster.ruleknowledge.domain.extraction.DocumentNodeType;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.Duration;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class RulebookPipelineApplicationService implements RulebookUploadProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(RulebookPipelineApplicationService.class);
    private static final Duration PROCESSING_LEASE = Duration.ofMinutes(10);
    private static final int PENDING_BATCH_SIZE = 10;

    private final RulebookRegistrationApplicationService registrationService;
    private final RulebookRegistrationRepository registrationRepository;
    private final RulebookFileStorage fileStorage;
    private final RulebookContentExtractor contentExtractor;
    private final SourcePreviewExtractor sourcePreviewExtractor;
    private final RulebookIndexingApplicationService indexingService;
    private final int embeddingDimension;
    private final DocumentExtractionPort structuredExtractor;
    private final RuleEvidenceProjectionApplicationService evidenceProjectionService;
    private final CanonicalCutoverPolicy canonicalCutoverPolicy;

    public RulebookPipelineApplicationService(
            RulebookRegistrationApplicationService registrationService,
            RulebookRegistrationRepository registrationRepository,
            RulebookFileStorage fileStorage,
            RulebookContentExtractor contentExtractor,
            SourcePreviewExtractor sourcePreviewExtractor,
            RulebookIndexingApplicationService indexingService,
            int embeddingDimension) {
        this(registrationService, registrationRepository, fileStorage, contentExtractor, sourcePreviewExtractor,
                indexingService, embeddingDimension, null, null, CanonicalCutoverPolicy.shadowOnly());
    }

    public RulebookPipelineApplicationService(
            RulebookRegistrationApplicationService registrationService,
            RulebookRegistrationRepository registrationRepository,
            RulebookFileStorage fileStorage,
            RulebookContentExtractor contentExtractor,
            SourcePreviewExtractor sourcePreviewExtractor,
            RulebookIndexingApplicationService indexingService,
            int embeddingDimension,
            DocumentExtractionPort structuredExtractor,
            RuleEvidenceProjectionApplicationService evidenceProjectionService) {
        this(registrationService, registrationRepository, fileStorage, contentExtractor, sourcePreviewExtractor,
                indexingService, embeddingDimension, structuredExtractor, evidenceProjectionService,
                CanonicalCutoverPolicy.shadowOnly());
    }

    public RulebookPipelineApplicationService(
            RulebookRegistrationApplicationService registrationService,
            RulebookRegistrationRepository registrationRepository,
            RulebookFileStorage fileStorage,
            RulebookContentExtractor contentExtractor,
            SourcePreviewExtractor sourcePreviewExtractor,
            RulebookIndexingApplicationService indexingService,
            int embeddingDimension,
            DocumentExtractionPort structuredExtractor,
            RuleEvidenceProjectionApplicationService evidenceProjectionService,
            CanonicalCutoverPolicy canonicalCutoverPolicy) {
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
        this.structuredExtractor = structuredExtractor;
        this.evidenceProjectionService = evidenceProjectionService;
        this.canonicalCutoverPolicy = Objects.requireNonNull(canonicalCutoverPolicy, "canonicalCutoverPolicy must not be null");
    }

    @Override
    public RulebookProcessingResult process(UploadRulebookCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        String contentHash = computeHash(command);

        Optional<StoredRulebookRegistration> existing = registrationRepository.findByOperationKey(command.operationKey());
        if (existing.isPresent()) {
            StoredRulebookRegistration previous = existing.get();
            if (!previous.contentHash().equals(contentHash)) {
                throw new RulebookPipelineException("conflict: same idempotency key with different file");
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
                command.originalFilename());
        registrationRepository.save(queued);
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

    private RulebookProcessingResult processClaimedRegistration(StoredRulebookRegistration registration) {
        if (registration.processingStatus() != ProcessingStatus.PROCESSING) {
            throw new IllegalStateException("only claimed document can be processed");
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
            projectRuleEvidence(registration, safeStoredContent);
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

    private void projectRuleEvidence(StoredRulebookRegistration registration, byte[] content) {
        if (structuredExtractor == null || evidenceProjectionService == null
                || (registration.format() != com.dndmaster.ruleknowledge.domain.rulebook.RulebookFormat.PDF
                && registration.format() != com.dndmaster.ruleknowledge.domain.rulebook.RulebookFormat.DOCX)) return;
        if (structuredExtractor instanceof NormalizedDocumentExtractionPort normalized) {
            var document = normalized.extractNormalized(registration.format(), content);
            var evidence = new StructuralEvidenceExtractor().extract(document);
            var skeleton = new AnchorSkeletonResolver().resolve(document, evidence).skeleton();
            var tree = new CanonicalHierarchyResolver("canonical-hierarchy.v1").resolve(document, skeleton);
            HierarchyMetrics metrics = HierarchyMetrics.from(tree);
            LOGGER.info("Canonical hierarchy shadow: document={}, sourceNodes={}, confirmedRatio={}, tentativeRatio={}, unresolvedRatio={}, preservationRatio={}, cycles={}, duplicateOwnership={}",
                    registration.rulebookId(), metrics.sourceNodes(), metrics.confirmedRatio(), metrics.tentativeRatio(),
                    metrics.unresolvedRatio(), metrics.preservationRatio(), metrics.cycles(), metrics.duplicateOwnership());
            if (canonicalCutoverPolicy.permits(metrics)) {
                evidenceProjectionService.projectCanonicalAndStore(registration.rulebookId(), registration.version() + 1,
                        document, tree);
                LOGGER.info("Canonical hierarchy cutover: document={}", registration.rulebookId());
                return;
            }
        }
        var extracted = structuredExtractor.extract(registration.format(), content);
        if (extracted.nodes().isEmpty()) return;
        DocumentNode root = new DocumentNode("root", DocumentNodeType.ROOT, 1, null, "", extracted.nodes(), List.of());
        evidenceProjectionService.projectAndStore(registration.rulebookId(), registration.version() + 1, root);
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
        String failure = extractionResult.failure().map(Enum::name).orElse("PARTIAL");
        LOGGER.error("Rulebook extraction failed: {}", failure);
        return "extraction failed";
    }

    private static String describeFailure(RuntimeException exception) {
        LOGGER.error("Rulebook processing failed", exception);
        return "processing failed";
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
                previewResult != null ? previewResult.assets() : registration.previewAssets());
    }
}
