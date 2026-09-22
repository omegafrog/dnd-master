package com.dndmaster.ruleknowledge.api;

import com.dndmaster.ruleknowledge.application.pipeline.BatchRulebookUploadApplicationService;
import com.dndmaster.ruleknowledge.application.pipeline.BatchRulebookUploadApplicationService.BatchUploadItem;
import com.dndmaster.ruleknowledge.application.pipeline.BatchRulebookUploadApplicationService.BatchUploadResult;
import com.dndmaster.ruleknowledge.application.indexing.IndexProgress;
import com.dndmaster.ruleknowledge.application.indexing.RulebookIndexRepository;
import com.dndmaster.ruleknowledge.application.pipeline.RulebookPipelineApplicationService;
import com.dndmaster.ruleknowledge.application.registration.RulebookRegistrationRepository;
import com.dndmaster.ruleknowledge.application.registration.StoredRulebookRegistration;
import com.dndmaster.ruleknowledge.application.preprocessing.PreprocessingPageState;
import com.dndmaster.ruleknowledge.application.auth.PlayerSessionLookupPort;
import com.dndmaster.ruleknowledge.application.definition.GameSystemDefinitionRepository;
import com.dndmaster.ruleknowledge.application.catalog.CatalogRulebookRepository;
import com.dndmaster.ruleknowledge.application.catalog.CatalogRulebookRevision;
import com.dndmaster.ruleknowledge.domain.definition.GameSystemDefinitionRevision;
import com.dndmaster.ruleknowledge.application.search.RuleEvidenceResult;
import com.dndmaster.ruleknowledge.application.search.RuleEvidenceSearchApplicationService;
import com.dndmaster.ruleknowledge.application.search.QueryIntent;
import com.dndmaster.ruleknowledge.application.search.SearchRuleEvidenceQuery;
import com.dndmaster.ruleknowledge.application.search.StorySourceEvidence;
import com.dndmaster.ruleknowledge.application.search.StorySourceScope;
import com.dndmaster.ruleknowledge.application.search.StorySourceSearchApplicationService;
import com.dndmaster.ruleknowledge.application.search.StorySourceSearchQuery;
import com.dndmaster.ruleknowledge.application.search.CharacterContextSearchApplicationService;
import com.dndmaster.ruleknowledge.application.search.CharacterContextDocumentScope;
import com.dndmaster.ruleknowledge.application.search.CharacterContextEvidence;
import com.dndmaster.ruleknowledge.application.publication.SourceProvenance;
import com.dndmaster.ruleknowledge.application.search.CharacterContextSearchQuery;
import com.dndmaster.ruleknowledge.application.search.AuthorizedDocumentScope;
import com.dndmaster.ruleknowledge.application.search.EvidenceCandidate;
import com.dndmaster.ruleknowledge.application.search.EvidenceSearchRequest;
import com.dndmaster.ruleknowledge.application.search.EvidenceSearchResult;
import com.dndmaster.ruleknowledge.application.search.EvidenceSearchUnavailableException;
import com.dndmaster.ruleknowledge.application.search.HybridEvidenceSearchService;
import com.dndmaster.ruleknowledge.domain.rulebook.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping
public class RuleKnowledgeController {
    private static final UUID CATALOG_OWNER = UUID.fromString("00000000-0000-0000-0000-000000000005");
    private final BatchRulebookUploadApplicationService batchUploadService;
    private final RulebookPipelineApplicationService pipelineService;
    private final RulebookRegistrationRepository registrationRepository;
    private final RuleEvidenceSearchApplicationService evidenceSearchService;
    private final StorySourceSearchApplicationService storySourceSearchService;
    private final CharacterContextSearchApplicationService characterContextSearchService;
    private final RulebookIndexRepository indexRepository;
    private final ObjectMapper objectMapper;
    private final GameSystemDefinitionRepository definitionRepository;
    private final String internalToken;
    private final CatalogRulebookRepository catalogRepository;
    private final PlayerSessionLookupPort playerSessionLookup;
    private final HybridEvidenceSearchService hybridEvidenceSearchService;

    public RuleKnowledgeController(
            RulebookPipelineApplicationService pipelineService,
            RulebookRegistrationRepository registrationRepository,
            RuleEvidenceSearchApplicationService evidenceSearchService,
            ObjectMapper objectMapper) {
        this(pipelineService, registrationRepository, evidenceSearchService, null, null, null, objectMapper, null, null, null, null, null);
    }

    public RuleKnowledgeController(
            RulebookPipelineApplicationService pipelineService,
            RulebookRegistrationRepository registrationRepository,
            RuleEvidenceSearchApplicationService evidenceSearchService,
            StorySourceSearchApplicationService storySourceSearchService,
            CharacterContextSearchApplicationService characterContextSearchService,
            RulebookIndexRepository indexRepository,
            ObjectMapper objectMapper) {
        this.pipelineService = pipelineService;
        this.batchUploadService = new BatchRulebookUploadApplicationService(pipelineService);
        this.registrationRepository = registrationRepository;
        this.evidenceSearchService = evidenceSearchService;
        this.storySourceSearchService = storySourceSearchService;
        this.characterContextSearchService = characterContextSearchService;
        this.indexRepository = indexRepository;
        this.objectMapper = objectMapper;
        this.definitionRepository = null;
        this.internalToken = "";
        this.catalogRepository = null;
        this.playerSessionLookup = null;
        this.hybridEvidenceSearchService = null;
    }

    public RuleKnowledgeController(
            RulebookPipelineApplicationService pipelineService,
            RulebookRegistrationRepository registrationRepository,
            RuleEvidenceSearchApplicationService evidenceSearchService,
            StorySourceSearchApplicationService storySourceSearchService,
            CharacterContextSearchApplicationService characterContextSearchService,
            RulebookIndexRepository indexRepository,
            ObjectMapper objectMapper,
            GameSystemDefinitionRepository definitionRepository,
            String internalToken) {
        this(pipelineService, registrationRepository, evidenceSearchService, storySourceSearchService,
                characterContextSearchService, indexRepository, objectMapper, definitionRepository, internalToken, null, null, null);
    }

    public RuleKnowledgeController(
            RulebookPipelineApplicationService pipelineService,
            RulebookRegistrationRepository registrationRepository,
            RuleEvidenceSearchApplicationService evidenceSearchService,
            StorySourceSearchApplicationService storySourceSearchService,
            CharacterContextSearchApplicationService characterContextSearchService,
            RulebookIndexRepository indexRepository,
            ObjectMapper objectMapper,
            GameSystemDefinitionRepository definitionRepository,
            String internalToken,
            CatalogRulebookRepository catalogRepository) {
        this(pipelineService, registrationRepository, evidenceSearchService, storySourceSearchService,
                characterContextSearchService, indexRepository, objectMapper, definitionRepository, internalToken, catalogRepository, null, null);
    }

    public RuleKnowledgeController(
            RulebookPipelineApplicationService pipelineService,
            RulebookRegistrationRepository registrationRepository,
            RuleEvidenceSearchApplicationService evidenceSearchService,
            StorySourceSearchApplicationService storySourceSearchService,
            CharacterContextSearchApplicationService characterContextSearchService,
            RulebookIndexRepository indexRepository,
            ObjectMapper objectMapper,
            GameSystemDefinitionRepository definitionRepository,
            String internalToken,
            CatalogRulebookRepository catalogRepository,
            PlayerSessionLookupPort playerSessionLookup) {
        this(pipelineService, registrationRepository, evidenceSearchService, storySourceSearchService, characterContextSearchService,
                indexRepository, objectMapper, definitionRepository, internalToken, catalogRepository, playerSessionLookup, null);
    }

    public RuleKnowledgeController(
            RulebookPipelineApplicationService pipelineService,
            RulebookRegistrationRepository registrationRepository,
            RuleEvidenceSearchApplicationService evidenceSearchService,
            StorySourceSearchApplicationService storySourceSearchService,
            CharacterContextSearchApplicationService characterContextSearchService,
            RulebookIndexRepository indexRepository,
            ObjectMapper objectMapper,
            GameSystemDefinitionRepository definitionRepository,
            String internalToken,
            CatalogRulebookRepository catalogRepository,
            PlayerSessionLookupPort playerSessionLookup,
            HybridEvidenceSearchService hybridEvidenceSearchService) {
        this.pipelineService = pipelineService;
        this.batchUploadService = new BatchRulebookUploadApplicationService(pipelineService);
        this.registrationRepository = registrationRepository;
        this.evidenceSearchService = evidenceSearchService;
        this.storySourceSearchService = storySourceSearchService;
        this.characterContextSearchService = characterContextSearchService;
        this.indexRepository = indexRepository;
        this.objectMapper = objectMapper;
        this.definitionRepository = definitionRepository;
        this.internalToken = internalToken == null ? "" : internalToken;
        this.catalogRepository = catalogRepository;
        this.playerSessionLookup = playerSessionLookup;
        this.hybridEvidenceSearchService = hybridEvidenceSearchService;
    }

    public RuleKnowledgeController(
            RulebookPipelineApplicationService pipelineService,
            RulebookRegistrationRepository registrationRepository,
            RuleEvidenceSearchApplicationService evidenceSearchService,
            StorySourceSearchApplicationService storySourceSearchService,
            CharacterContextSearchApplicationService characterContextSearchService,
            ObjectMapper objectMapper) {
        this(pipelineService, registrationRepository, evidenceSearchService, storySourceSearchService,
                characterContextSearchService, null, objectMapper);
    }

    public RuleKnowledgeController(
            RulebookPipelineApplicationService pipelineService,
            RulebookRegistrationRepository registrationRepository,
            RuleEvidenceSearchApplicationService evidenceSearchService,
            StorySourceSearchApplicationService storySourceSearchService,
            ObjectMapper objectMapper) {
        this(pipelineService, registrationRepository, evidenceSearchService, storySourceSearchService, null, null, objectMapper);
    }

    @PostMapping("/api/v1/rulebooks")
    ResponseEntity<BatchUploadResponse> uploadRulebooks(
            @RequestParam("ownerPlayerId") UUID ownerPlayerId,
            @RequestPart("documents") MultipartFile documents,
            @RequestPart("files") List<MultipartFile> files,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Internal-Token", required = false) String internalServiceToken,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) throws IOException {
        requireBrowserOwnerOrInternal(authorization, internalServiceToken, ownerPlayerId);
        List<UploadDocumentRequest> uploadDocuments = parseDocuments(documents.getBytes());
        if (uploadDocuments.size() != files.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "documents and files must have the same size");
        }
        List<BatchUploadItem> items = new java.util.ArrayList<>(files.size());
        for (int index = 0; index < files.size(); index++) {
            MultipartFile file = files.get(index);
            UploadDocumentRequest document = uploadDocuments.get(index);
            String operationKey = document.idempotencyKey();
            if ((operationKey == null || operationKey.isBlank()) && files.size() == 1) {
                operationKey = idempotencyKey;
            }
            if (operationKey == null || operationKey.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idempotency key must not be blank");
            }
            if (document.documentType() != DocumentType.STORYBOOK) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "user uploads accept STORYBOOK only; rulebooks are selected from the shared catalog");
            }
            String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : document.originalFilename();
            items.add(new BatchUploadItem(
                    operationKey,
                    new OwnerPlayerId(ownerPlayerId),
                    document.documentType(),
                    resolveFormat(originalFilename),
                    originalFilename,
                    file.getBytes()));
        }
        List<BatchUploadResult> results = batchUploadService.process(items);
        results.stream()
                .filter(result -> "CONFLICT".equals(result.status()))
                .findFirst()
                .ifPresent(result -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, result.failureReason());
                });
        return ResponseEntity.accepted().body(new BatchUploadResponse(results));
    }

    @GetMapping("/api/v1/rulebooks/{rulebookId}")
    RulebookStatusResponse rulebookStatus(@PathVariable UUID rulebookId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        StoredRulebookRegistration registration = registrationRepository.findById(new RulebookId(rulebookId))
                .orElse(null);
        if (registration != null) {
            requireOwner(authenticatedPlayerId(authorization), registration.ownerPlayerId().value());
        } else {
            authenticatedPlayerId(authorization);
        }
        return java.util.Optional.ofNullable(registration)
                .map(r -> new RulebookStatusResponse(
                        rulebookId,
                        r.knowledgeDocumentId().value(),
                        r.processingStatus().name(),
                        r.documentType(),
                        r.originalFilename(),
                        r.failureCode(),
                        r.version(),
                        warningsFor(r), progressFor(r), r.candidateExtractionVersion(), r.preprocessingPages(), retryabilityFor(r), reviewQuestionsFor(r)))
                .orElse(new RulebookStatusResponse(rulebookId, null, "NOT_FOUND", null, null, null, 0L, List.of(), null, null, List.of(),
                        new RetryabilityView(false, List.of(), List.of("DOCUMENT_NOT_FOUND")), List.of()));
    }

    /** Package-local compatibility helper for callers that already hold the registration. */
    RulebookStatusResponse rulebookStatus(UUID rulebookId) {
        return statusFor(rulebookId);
    }

    private RulebookStatusResponse statusFor(UUID rulebookId) {
        return registrationRepository.findById(new RulebookId(rulebookId))
                .map(r -> new RulebookStatusResponse(
                        rulebookId, r.knowledgeDocumentId().value(), r.processingStatus().name(), r.documentType(),
                        r.originalFilename(), r.failureCode(), r.version(), warningsFor(r), progressFor(r),
                        r.candidateExtractionVersion(), r.preprocessingPages(), retryabilityFor(r), reviewQuestionsFor(r)))
                .orElse(new RulebookStatusResponse(rulebookId, null, "NOT_FOUND", null, null, null, 0L, List.of(), null,
                        null, List.of(), new RetryabilityView(false, List.of(), List.of("DOCUMENT_NOT_FOUND")), List.of()));
    }

    private DocumentProgressView progressFor(StoredRulebookRegistration registration) {
        if (registration.processingStatus() == ProcessingStatus.INDEXED
                || registration.processingStatus() == ProcessingStatus.PARTIAL_CONFIRMED) {
            return new DocumentProgressView("READY", 100, null, null, null);
        }
        if (registration.processingStatus() == ProcessingStatus.NEEDS_REVIEW) {
            return new DocumentProgressView("NEEDS_REVIEW", 0, null, null, registration.failureCode());
        }
        if (registration.processingStatus() == ProcessingStatus.FAILED
                || registration.processingStatus() == ProcessingStatus.NEEDS_INPUT
                || registration.processingStatus() == ProcessingStatus.REJECTED) {
            return new DocumentProgressView("FAILED", 0, null, null, registration.failureCode());
        }
        if (registration.processingStatus() == ProcessingStatus.VALIDATED) {
            return new DocumentProgressView("VALIDATED", 75, null, null, null);
        }
        if (indexRepository != null) {
            var indexProgress = indexRepository.progressFor(registration.rulebookId(), "v1-" + registration.contentHash());
            if (indexProgress.isPresent()) {
                var progress = indexProgress.get();
                int percent = progress.totalChunks() == 0
                        ? 50
                        : 50 + (int) Math.round(50.0 * progress.completedChunks() / progress.totalChunks());
                return new DocumentProgressView("EMBEDDING", percent, progress.completedChunks(), progress.totalChunks(), progress.lastError());
            }
        }
        return switch (registration.processingStatus()) {
            case EXTRACTED, PARTIAL_AWAITING_CONFIRMATION -> new DocumentProgressView("CHUNKING", 50, null, null, null);
            case PROCESSING -> new DocumentProgressView("EXTRACTING", 25, null, null, null);
            default -> new DocumentProgressView("QUEUED", 0, null, null, null);
        };
    }

    @GetMapping("/api/v1/rulebooks/{rulebookId}/source-preview")
    ResponseEntity<SourcePreviewResponse> sourcePreview(@PathVariable UUID rulebookId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Internal-Token", required = false) String internalServiceToken) {
        StoredRulebookRegistration registration = registrationRepository.findById(new RulebookId(rulebookId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "knowledge document not found"));
        if (!isValidInternalToken(internalServiceToken)) {
            requireOwner(authenticatedPlayerId(authorization), registration.ownerPlayerId().value());
        }
        return sourcePreviewFor(registration);
    }

    ResponseEntity<SourcePreviewResponse> sourcePreview(UUID rulebookId) {
        return sourcePreviewFor(registrationRepository.findById(new RulebookId(rulebookId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "knowledge document not found")));
    }

    private ResponseEntity<SourcePreviewResponse> sourcePreviewFor(StoredRulebookRegistration registration) {
        SourcePreviewResult preview = registration.sourcePreviewResult();
        String content = preview.content();
        if (content == null || content.isBlank()) content = registration.extractedContent();
        if (content == null || content.isBlank()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "source preview requires extracted content");
        }
        return ResponseEntity.ok(new SourcePreviewResponse(
                registration.rulebookId().value(),
                registration.knowledgeDocumentId().value(),
                registration.documentType(),
                registration.originalFilename(),
                registration.format(),
                registration.processingStatus().name(),
                content,
                registration.version(),
                warningsFor(registration),
                preview.spans().stream()
                        .map(span -> new PreviewSpanView(
                                span.kind(),
                                span.path(),
                                span.pageNumber(),
                                span.bounds(),
                                span.lineNumber(),
                                span.startInclusive(),
                                span.endExclusive(),
                                span.text(),
                                span.locator(),
                                span.sourceMethod(),
                                span.confidence()))
                        .toList(),
                preview.assets().stream()
                        .map(asset -> new PreviewAssetView(asset.kind(), asset.locator(), asset.contentType(), asset.pageNumber()))
                        .toList()));
    }

    @GetMapping("/internal/v1/rulebooks/{rulebookId}/game-system-definition")
    GameSystemDefinitionResponse gameSystemDefinition(@PathVariable UUID rulebookId,
            @RequestParam(required = false) Long version,
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        requireInternalToken(token);
        if (definitionRepository == null) throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED);
        requirePublishedCatalogRulebook(rulebookId);
        return (version == null ? definitionRepository.findPublished(rulebookId) : definitionRepository.findPublished(rulebookId, version))
                .map(revision -> new GameSystemDefinitionResponse(revision.rulebookId(), revision.version(), revision.definitionJson()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "published game system definition not found"));
    }

    @PostMapping("/internal/v1/rulebooks/{rulebookId}/game-system-definition")
    GameSystemDefinitionResponse publishGameSystemDefinition(@PathVariable UUID rulebookId,
            @RequestBody GameSystemDefinitionRequest request,
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        requireInternalToken(token);
        if (definitionRepository == null) throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED);
        requirePublishedCatalogRulebook(rulebookId);
        if (request == null || request.version() <= 0 || request.definitionJson() == null || request.definitionJson().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "definition request is invalid");
        }
        GameSystemDefinitionRevision revision = GameSystemDefinitionRevision.draft(
                rulebookId, request.version(), request.definitionJson()).publish();
        definitionRepository.save(revision);
        return new GameSystemDefinitionResponse(rulebookId, revision.version(), revision.definitionJson());
    }

    private void requireInternalToken(String token) {
        if (token == null || token.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "internal token is required");
        }
        if (internalToken.isBlank() || !internalToken.equals(token)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "invalid internal token");
        }
    }

    @PostMapping("/api/v1/rulebooks/{rulebookId}/retry")
    RulebookStatusResponse retryRulebook(@PathVariable UUID rulebookId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        StoredRulebookRegistration registration = registrationRepository.findById(new RulebookId(rulebookId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "knowledge document not found"));
        requireOwner(authenticatedPlayerId(authorization), registration.ownerPlayerId().value());
        try {
            pipelineService.retry(new RulebookId(rulebookId));
            return statusFor(rulebookId);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage(), exception);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage(), exception);
        }
    }

    @PostMapping("/api/v1/rulebooks/{rulebookId}/retry-pages")
    RulebookStatusResponse retryPages(
            @PathVariable UUID rulebookId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody RetryPagesRequest request) {
        StoredRulebookRegistration registration = registrationRepository.findById(new RulebookId(rulebookId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "knowledge document not found"));
        requireOwner(authenticatedPlayerId(authorization), registration.ownerPlayerId().value());
        try {
            pipelineService.retryPages(new RulebookId(rulebookId), request == null ? null : request.requestId(),
                    request == null ? null : request.pages(), request == null ? java.util.Map.of() : request.layoutSelections());
            return rulebookStatus(rulebookId);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage(), exception);
        }
    }

    @DeleteMapping("/api/v1/rulebooks/{rulebookId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteRulebook(@PathVariable UUID rulebookId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        UUID authenticatedOwner = authenticatedPlayerId(authorization);
        StoredRulebookRegistration registration = registrationRepository.findById(new RulebookId(rulebookId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "knowledge document not found"));
        requireOwner(authenticatedOwner, registration.ownerPlayerId().value());
        try {
            pipelineService.delete(new RulebookId(rulebookId), new OwnerPlayerId(authenticatedOwner));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage(), exception);
        } catch (SecurityException exception) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, exception.getMessage(), exception);
        }
    }

    @PostMapping("/api/v1/rulebooks/rule-set")
    ResponseEntity<Void> saveRuleSet(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody RuleSetSaveRequest request) {
        UUID ownerId = authenticatedPlayerId(authorization);
        if (request == null || request.knowledgeDocumentIds() == null || request.knowledgeDocumentIds().isEmpty()
                || request.knowledgeDocumentIds().stream().anyMatch(java.util.Objects::isNull)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "knowledgeDocumentIds must not be empty");
        }
        List<UUID> knowledgeDocumentIds = request.knowledgeDocumentIds();
        Set<UUID> selectedKnowledgeDocumentIds = new HashSet<>(knowledgeDocumentIds);
        if (isCatalogScope(knowledgeDocumentIds)) {
            return ResponseEntity.noContent().build();
        }
        Set<UUID> ownedKnowledgeDocumentIds = registrationRepository.findByOwner(new OwnerPlayerId(ownerId)).stream()
                .map(registration -> registration.knowledgeDocumentId().value())
                .collect(java.util.stream.Collectors.toSet());
        if (!ownedKnowledgeDocumentIds.containsAll(selectedKnowledgeDocumentIds)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "knowledgeDocumentIds must belong to the authenticated owner");
        }
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/internal/v1/rulebooks")
    ResponseEntity<?> ownedRulebooks(
            @RequestParam UUID ownerId,
            @RequestHeader(value = "X-Internal-Token", required = false) String internalServiceToken,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        if (!isValidInternalToken(internalServiceToken)) {
            try {
                requireOwner(authenticatedPlayerId(authorization), ownerId);
            } catch (ResponseStatusException exception) {
                HttpStatus status = exception.getStatusCode().value() == HttpStatus.FORBIDDEN.value()
                        ? HttpStatus.FORBIDDEN : HttpStatus.UNAUTHORIZED;
                return evidenceSearchError(status,
                        status == HttpStatus.FORBIDDEN ? "RULEBOOK_LOOKUP_FORBIDDEN" : "RULEBOOK_LOOKUP_UNAUTHENTICATED");
            }
        }
        List<StoredRulebookRegistration> registrations = registrationRepository.findByOwner(new OwnerPlayerId(ownerId));
        List<RulebookSummary> summaries = registrations.stream()
                .filter(r -> r.documentType() != DocumentType.RULEBOOK || !isCatalogRegistration(r))
                .map(r -> new RulebookSummary(
                        r.rulebookId().value(), r.knowledgeDocumentId().value(), r.processingStatus().name(),
                        r.format().name(), r.documentType(), r.originalFilename(), r.failureCode(),
                        r.version(), warningsFor(r), progressFor(r), reviewQuestionsFor(r), r.preprocessingPages()))
                .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
        return ResponseEntity.ok(new OwnedRulebooksResponse(ownerId, summaries));
    }

    private boolean isValidInternalToken(String token) {
        return !internalToken.isBlank() && internalToken.equals(token);
    }

    @GetMapping("/internal/v1/rulebooks/published-catalog")
    ResponseEntity<?> publishedCatalogRulebooks(
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        if (token == null || token.isBlank()) {
            return evidenceSearchError(HttpStatus.UNAUTHORIZED, "RULEBOOK_CATALOG_UNAUTHENTICATED");
        }
        if (internalToken.isBlank() || !internalToken.equals(token)) {
            return evidenceSearchError(HttpStatus.FORBIDDEN, "RULEBOOK_CATALOG_FORBIDDEN");
        }
        List<RulebookSummary> summaries = catalogRepository == null
                ? List.of()
                : catalogRepository.findAll().stream()
                        .filter(item -> item.status() == com.dndmaster.ruleknowledge.domain.catalog.CatalogRevisionStatus.READY
                                && item.published() && item.rulebookId() != null)
                        .map(item -> registrationRepository.findById(new RulebookId(item.rulebookId()))
                                .filter(registration -> registration.processingStatus() == ProcessingStatus.INDEXED
                                        && registration.documentType() == DocumentType.RULEBOOK
                                        && registration.ownerPlayerId().value().equals(CATALOG_OWNER)
                                        && registration.version() > 0)
                                .map(registration -> new RulebookSummary(
                                        registration.rulebookId().value(), registration.rulebookId().value(),
                                        registration.processingStatus().name(), registration.format().name(),
                                        registration.documentType(), registration.originalFilename(), registration.failureCode(),
                                        registration.version(), warningsFor(registration), progressFor(registration),
                                        reviewQuestionsFor(registration), registration.preprocessingPages()))
                                .orElse(null))
                        .filter(java.util.Objects::nonNull)
                        .toList();
        return ResponseEntity.ok(new OwnedRulebooksResponse(CATALOG_OWNER, summaries));
    }

    @GetMapping("/internal/v1/rulebook-indexes")
    OwnedIndexesResponse ownedIndexes(@RequestParam UUID ownerId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        requireInternalToken(token);
        return new OwnedIndexesResponse(ownerId, List.of());
    }

    @GetMapping("/internal/v1/rulebooks/{rulebookId}/ownership")
    OwnershipResponse rulebookOwnership(@PathVariable UUID rulebookId, @RequestParam UUID playerId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        requireInternalToken(token);
        boolean owned = isPublishedCatalogScope(List.of(rulebookId)) || registrationRepository.findById(new RulebookId(rulebookId))
                .filter(r -> r.processingStatus() == ProcessingStatus.INDEXED)
                .filter(r -> r.documentType() != DocumentType.RULEBOOK || !isCatalogRegistration(r))
                .map(r -> r.ownerPlayerId().value().equals(playerId))
                .orElse(false);
        return new OwnershipResponse(rulebookId, playerId, owned);
    }

    @PostMapping("/internal/v1/evidence-candidates/search")
    ResponseEntity<?> searchEvidenceCandidates(
            @RequestHeader(value = "X-Internal-Token", required = false) String internalServiceToken,
            @RequestBody UnifiedEvidenceCandidateSearchRequest request) {
        try {
            if (hybridEvidenceSearchService == null) {
                return evidenceSearchError(HttpStatus.SERVICE_UNAVAILABLE, "EVIDENCE_SEARCH_UNAVAILABLE");
            }
            requireInternalToken(internalServiceToken);
            if (request.ownerId() == null || request.sessionId() == null || request.scenarioPackageId() == null || request.stageKey() == null
                    || request.stageKey().isBlank() || request.query() == null || request.query().isBlank()
                    || request.scope() == null || request.scope().isEmpty()
                    || request.denseLimit() < 1 || request.denseLimit() > 30
                    || request.bm25Limit() < 1 || request.bm25Limit() > 30) {
                return evidenceSearchError(HttpStatus.BAD_REQUEST, "EVIDENCE_SEARCH_INVALID_REQUEST");
            }
            Set<String> scopeKeys = new HashSet<>();
            List<AuthorizedDocumentScope> scope = new java.util.ArrayList<>();
            for (EvidenceCandidateScopeRequest item : request.scope()) {
                if (item == null || item.documentId() == null || item.documentType() == null || item.extractionVersion() <= 0
                        || !scopeKeys.add(item.documentId() + ":" + item.extractionVersion())) {
                    return evidenceSearchError(HttpStatus.BAD_REQUEST, "EVIDENCE_SEARCH_INVALID_REQUEST");
                }
                StoredRulebookRegistration registration = registrationRepository.findById(new RulebookId(item.documentId()))
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "document scope is not authorized"));
                boolean publishedCatalogRulebook = item.documentType() == DocumentType.RULEBOOK
                        && isPublishedCatalogScope(List.of(item.documentId()));
                if (!publishedCatalogRulebook && !registration.ownerPlayerId().value().equals(request.ownerId())) {
                    return evidenceSearchError(HttpStatus.FORBIDDEN, "EVIDENCE_SEARCH_SCOPE_FORBIDDEN");
                }
                if (registration.processingStatus() != ProcessingStatus.INDEXED || registration.version() != item.extractionVersion()
                        || registration.documentType() != item.documentType()) {
                    return evidenceSearchError(HttpStatus.BAD_REQUEST, "EVIDENCE_SEARCH_INVALID_REQUEST");
                }
                scope.add(new AuthorizedDocumentScope(new KnowledgeDocumentId(item.documentId()), item.extractionVersion(), item.documentType(),
                        new OwnerPlayerId(publishedCatalogRulebook ? CATALOG_OWNER : request.ownerId())));
            }
            EvidenceSearchResult result = hybridEvidenceSearchService.search(new com.dndmaster.ruleknowledge.application.search.EvidenceSearchRequest(
                    new OwnerPlayerId(request.ownerId()), request.sessionId(), request.scenarioPackageId(), request.stageKey(),
                    request.actionIntent(), scope, request.activeLocators(), request.query(), request.denseLimit(), request.bm25Limit()));
            return ResponseEntity.ok(new UnifiedEvidenceCandidateSearchResponse(request.ownerId(), request.sessionId(),
                    request.scenarioPackageId(), result.candidates().stream().map(this::candidateResponse).toList()));
        } catch (EvidenceSearchUnavailableException exception) {
            return evidenceSearchError(HttpStatus.SERVICE_UNAVAILABLE, "EVIDENCE_SEARCH_UNAVAILABLE");
        } catch (ResponseStatusException exception) {
            int status = exception.getStatusCode().value();
            return evidenceSearchError(status == 401 ? HttpStatus.UNAUTHORIZED
                    : status == 400 ? HttpStatus.BAD_REQUEST : HttpStatus.FORBIDDEN,
                    status == 401 ? "EVIDENCE_SEARCH_UNAUTHENTICATED"
                            : status == 400 ? "EVIDENCE_SEARCH_INVALID_REQUEST" : "EVIDENCE_SEARCH_SCOPE_FORBIDDEN");
        } catch (IllegalArgumentException exception) {
            return evidenceSearchError(HttpStatus.BAD_REQUEST, "EVIDENCE_SEARCH_INVALID_REQUEST");
        }
    }

    @PostMapping("/internal/v1/evidence-candidates/preparation-search")
    ResponseEntity<?> searchPreparationEvidenceCandidates(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestBody PreparationEvidenceCandidateSearchRequest request) {
        try {
            if (hybridEvidenceSearchService == null) {
                return evidenceSearchError(HttpStatus.SERVICE_UNAVAILABLE, "EVIDENCE_SEARCH_UNAVAILABLE");
            }
            requireInternalToken(token);
            if (request.ownerId() == null || request.scenarioSourceBundleId() == null || request.query() == null
                    || request.query().isBlank() || request.scope() == null || request.scope().isEmpty()
                    || request.denseLimit() < 1 || request.denseLimit() > 30
                    || request.bm25Limit() < 1 || request.bm25Limit() > 30) {
                return evidenceSearchError(HttpStatus.BAD_REQUEST, "EVIDENCE_SEARCH_INVALID_REQUEST");
            }
            Set<String> scopeKeys = new HashSet<>();
            List<AuthorizedDocumentScope> scope = new java.util.ArrayList<>();
            for (EvidenceCandidateScopeRequest item : request.scope()) {
                if (item == null || item.documentId() == null || (item.documentType() != DocumentType.STORYBOOK
                        && item.documentType() != DocumentType.RULEBOOK)
                        || item.extractionVersion() <= 0 || !scopeKeys.add(item.documentId() + ":" + item.extractionVersion())) {
                    return evidenceSearchError(HttpStatus.BAD_REQUEST, "EVIDENCE_SEARCH_INVALID_REQUEST");
                }
                StoredRulebookRegistration registration = registrationRepository.findById(new RulebookId(item.documentId()))
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "document scope is not authorized"));
                boolean publishedCatalogRulebook = item.documentType() == DocumentType.RULEBOOK
                        && isPublishedCatalogScope(List.of(item.documentId()));
                if (!publishedCatalogRulebook && !registration.ownerPlayerId().value().equals(request.ownerId())) {
                    return evidenceSearchError(HttpStatus.FORBIDDEN, "EVIDENCE_SEARCH_SCOPE_FORBIDDEN");
                }
                if (registration.processingStatus() != ProcessingStatus.INDEXED || registration.version() != item.extractionVersion()
                        || registration.documentType() != item.documentType()) {
                    return evidenceSearchError(HttpStatus.BAD_REQUEST, "EVIDENCE_SEARCH_INVALID_REQUEST");
                }
                scope.add(new AuthorizedDocumentScope(new KnowledgeDocumentId(item.documentId()), item.extractionVersion(),
                        item.documentType(), new OwnerPlayerId(publishedCatalogRulebook ? CATALOG_OWNER : request.ownerId())));
            }
            EvidenceSearchResult result = hybridEvidenceSearchService.search(
                    new com.dndmaster.ruleknowledge.application.search.PreparationEvidenceSearchRequest(
                            new OwnerPlayerId(request.ownerId()), request.scenarioSourceBundleId(), scope,
                            request.activeLocators(), request.query(), request.denseLimit(), request.bm25Limit()).asSharedSearchRequest());
            return ResponseEntity.ok(new PreparationEvidenceCandidateSearchResponse(request.ownerId(), request.scenarioSourceBundleId(),
                    result.candidates().stream().map(this::candidateResponse).toList()));
        } catch (EvidenceSearchUnavailableException exception) {
            return evidenceSearchError(HttpStatus.SERVICE_UNAVAILABLE, "EVIDENCE_SEARCH_UNAVAILABLE");
        } catch (ResponseStatusException exception) {
            int status = exception.getStatusCode().value();
            return evidenceSearchError(status == 401 ? HttpStatus.UNAUTHORIZED
                    : status == 400 ? HttpStatus.BAD_REQUEST : HttpStatus.FORBIDDEN,
                    status == 401 ? "EVIDENCE_SEARCH_UNAUTHENTICATED"
                            : status == 400 ? "EVIDENCE_SEARCH_INVALID_REQUEST" : "EVIDENCE_SEARCH_SCOPE_FORBIDDEN");
        } catch (IllegalArgumentException exception) {
            return evidenceSearchError(HttpStatus.BAD_REQUEST, "EVIDENCE_SEARCH_INVALID_REQUEST");
        }
    }

    private static ResponseEntity<EvidenceSearchErrorResponse> evidenceSearchError(HttpStatus status, String code) {
        return ResponseEntity.status(status).body(new EvidenceSearchErrorResponse(code));
    }

    private UnifiedEvidenceCandidateResponse candidateResponse(EvidenceCandidate candidate) {
        return new UnifiedEvidenceCandidateResponse(candidate.chunkId().value(), candidate.documentId().value(),
                candidate.extractionVersion(), candidate.documentType(), candidate.locator(), candidate.excerpt(), candidate.provenance(),
                candidate.denseRank(), candidate.bm25Rank(), candidate.rrfScore());
    }

    @PostMapping("/internal/v1/rule-evidence/search")
    EvidenceSearchResponse searchEvidence(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestBody EvidenceSearchRequest request) {
        requireInternalToken(token);
        if (request == null || request.ownerId() == null || request.rulebookIds() == null
                || request.situation() == null || request.situation().isBlank() || request.queryIntent() == null
                || (request.limit() != null && (request.limit() < 1 || request.limit() > 30))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "evidence search request is invalid");
        }
        boolean catalogScope = isPublishedCatalogScope(request.rulebookIds());
        List<UUID> authorizedRulebookIds = catalogScope
                ? request.rulebookIds()
                : authorizeDocuments(request.ownerId(), request.rulebookIds(), DocumentType.RULEBOOK);
        List<RulebookId> rulebookIds = authorizedRulebookIds.stream()
                .map(RulebookId::new)
                .toList();
        SearchRuleEvidenceQuery query = new SearchRuleEvidenceQuery(
                new OwnerPlayerId(catalogScope ? CATALOG_OWNER : request.ownerId()),
                rulebookIds,
                request.situation(),
                request.queryIntent(),
                request.limit() != null ? request.limit() : 5);
        List<RuleEvidenceResult> results = evidenceSearchService.search(query);
        List<EvidenceItem> evidence = results.stream()
                .map(r -> new EvidenceItem(
                        r.rulebookId().value(),
                        r.chunkId().value(),
                        r.locator(),
                        r.excerpt(),
                        r.score(),
                        r.chapter(),
                        r.section(),
                        provenanceView(r.rulebookId().value(), r.extractionVersion(), r.provenance()),
                        runtimeCitationKey("RULEBOOK", r.rulebookId().value(), r.extractionVersion(), r.locator())))
                .toList();
        return new EvidenceSearchResponse(request.ownerId(), evidence);
    }

    @PostMapping("/internal/v1/story-sources/search")
    StorySourceSearchResponse searchStorySources(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestBody StorySourceSearchRequest request) {
        requireInternalToken(token);
        if (request == null || request.ownerId() == null || request.documents() == null || request.documents().isEmpty()
                || request.documents().stream().anyMatch(java.util.Objects::isNull)
                || request.situation() == null || request.situation().isBlank()
                || (request.limit() != null && (request.limit() < 1 || request.limit() > 30))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "story source search request is invalid");
        }
        if (storySourceSearchService == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "story source search is not configured");
        }
        List<UUID> authorizedStorybookIds = authorizeDocuments(request.ownerId(), request.documents().stream()
                .map(StorySourceScopeRequest::documentId).toList(), DocumentType.STORYBOOK);
        request.documents().forEach(document -> requireIndexedVersion(document.documentId(), document.extractionVersion()));
        List<StorySourceScope> scope = request.documents().stream()
                .filter(document -> authorizedStorybookIds.contains(document.documentId()))
                .map(document -> new StorySourceScope(
                        new KnowledgeDocumentId(document.documentId()), document.extractionVersion()))
                .toList();
        List<StorySourceEvidence> evidence = storySourceSearchService.search(new StorySourceSearchQuery(
                new OwnerPlayerId(request.ownerId()),
                scope,
                request.activeLocators(),
                request.situation(),
                request.limit() != null ? request.limit() : 5));
        return new StorySourceSearchResponse(
                request.ownerId(),
                evidence.stream()
                        .map(result -> new StorySourceEvidenceItem(
                                result.documentId().value(), result.extractionVersion(), result.sourceSpanLocator(),
                                result.excerpt(), result.score(), provenanceView(result.documentId().value(),
                                        result.extractionVersion(), result.provenance()),
                                runtimeCitationKey("STORYBOOK", result.documentId().value(),
                                        result.extractionVersion(), result.sourceSpanLocator())))
                .toList());
    }

    @PostMapping("/internal/v1/character-context/search")
    CharacterContextSearchResponse searchCharacterContext(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestBody CharacterContextSearchRequest request) {
        requireInternalToken(token);
        if (request == null || request.ownerId() == null || request.situation() == null || request.situation().isBlank()
                || request.documents() == null || request.documents().isEmpty()
                || request.documents().stream().anyMatch(java.util.Objects::isNull)
                || (request.tokenBudget() != null && request.tokenBudget() < 1)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "character context search request is invalid");
        }
        if (characterContextSearchService == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "character context search is not configured");
        }
        if (request.documents() == null || request.documents().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "document scope must not be empty");
        }
        Map<DocumentType, List<CharacterContextDocumentScope>> scope = new java.util.EnumMap<>(DocumentType.class);
        List<AuthorizedDocumentScope> hybridScope = new java.util.ArrayList<>();
        Set<String> seen = new HashSet<>();
        boolean catalogScope = isPublishedCatalogScope(request.documents().stream().map(CharacterContextScopeRequest::documentId).toList());
        for (CharacterContextScopeRequest document : request.documents()) {
            if (document.documentId() == null || document.extractionVersion() <= 0 || document.documentType() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "document scope is invalid");
            }
            if (!seen.add(document.documentId() + ":" + document.extractionVersion())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "document scope must not contain duplicates");
            }
            StoredRulebookRegistration registration = registrationRepository.findById(new RulebookId(document.documentId()))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "document is not registered"));
            if (!catalogScope && !registration.ownerPlayerId().value().equals(request.ownerId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "document does not belong to authenticated player");
            }
            if (registration.processingStatus() != ProcessingStatus.INDEXED
                    || registration.version() != document.extractionVersion()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "document scope is not indexed at requested version");
            }
            if (registration.documentType() != document.documentType()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "document type does not match registration");
            }
            scope.computeIfAbsent(document.documentType(), ignored -> new java.util.ArrayList<>())
                    .add(new CharacterContextDocumentScope(
                            new KnowledgeDocumentId(document.documentId()), document.extractionVersion()));
            hybridScope.add(new AuthorizedDocumentScope(new KnowledgeDocumentId(document.documentId()),
                    document.extractionVersion(), document.documentType(),
                    new OwnerPlayerId(catalogScope ? CATALOG_OWNER : request.ownerId())));
        }
        if (hybridEvidenceSearchService != null) {
            EvidenceSearchResult result = hybridEvidenceSearchService.search(new com.dndmaster.ruleknowledge.application.search.EvidenceSearchRequest(
                    new OwnerPlayerId(catalogScope ? CATALOG_OWNER : request.ownerId()), null, null,
                    "character-context", "CHARACTER_CONTEXT", hybridScope, List.of(), request.situation(), 30, 30));
            return new CharacterContextSearchResponse(request.ownerId(), result.candidates().stream()
                    .map(candidate -> new CharacterContextEvidenceItem(candidate.documentId().value(), candidate.documentType(),
                            candidate.extractionVersion(), candidate.locator(), candidate.excerpt(), candidate.rrfScore()))
                    .toList());
        }
        Map<DocumentType, Double> thresholds = request.thresholds() == null ? Map.of() : request.thresholds();
        List<CharacterContextEvidence> evidence = characterContextSearchService.search(new CharacterContextSearchQuery(
                new OwnerPlayerId(catalogScope ? CATALOG_OWNER : request.ownerId()), scope, thresholds, request.situation(),
                request.tokenBudget() == null ? 2000 : request.tokenBudget()));
        return new CharacterContextSearchResponse(request.ownerId(), evidence.stream()
                .map(result -> new CharacterContextEvidenceItem(
                        result.documentId().value(), result.documentType(), result.extractionVersion(),
                        result.locator(), result.excerpt(), result.similarity()))
                .toList());
    }

    @GetMapping("/internal/v1/story-sources/{documentId}/context")
    StorySourceContextResponse readStorySourceContext(
            @PathVariable UUID documentId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestParam UUID ownerId,
            @RequestParam long extractionVersion,
            @RequestParam String locator) {
        requireInternalToken(token);
        if (ownerId == null || extractionVersion <= 0 || locator == null || locator.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "story source context request is invalid");
        }
        StoredRulebookRegistration registration = registrationRepository.findById(new RulebookId(documentId))
                .filter(candidate -> candidate.ownerPlayerId().value().equals(ownerId))
                .filter(candidate -> candidate.documentType() == DocumentType.STORYBOOK)
                .filter(candidate -> candidate.processingStatus() == ProcessingStatus.INDEXED)
                .filter(candidate -> candidate.version() == extractionVersion)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "story source context not found"));
        List<PreviewSpan> spans = registration.previewSpans();
        int selected = -1;
        for (int index = 0; index < spans.size(); index++) {
            if (spans.get(index).locator().equals(locator)) {
                selected = index;
                break;
            }
        }
        if (selected < 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "story source span not found");
        }
        int from = Math.max(0, selected - 2);
        int to = Math.min(spans.size(), selected + 3);
        return new StorySourceContextResponse(
                documentId,
                extractionVersion,
                locator,
                spans.subList(from, to).stream()
                        .map(span -> new StorySourceSpanItem(span.locator(), span.text(), span.pageNumber(), span.sourceMethod()))
                        .toList());
    }

    private List<UploadDocumentRequest> parseDocuments(byte[] documentsJson) throws IOException {
        try {
            return objectMapper.readValue(
                    new String(documentsJson, StandardCharsets.UTF_8),
                    new TypeReference<List<UploadDocumentRequest>>() {});
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "documents must be valid JSON", exception);
        }
    }

    private UUID authenticatedPlayerId(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Bearer authorization is required");
        }
        String token = authorization.substring("Bearer ".length());
        if (playerSessionLookup == null || token.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Bearer authorization is invalid");
        }
        return playerSessionLookup.resolvePlayerId(token)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Bearer authorization is invalid"));
    }

    private void requireBrowserOwnerOrInternal(String authorization, String token, UUID requestedOwner) {
        if (isValidInternalToken(token)) return;
        requireOwner(authenticatedPlayerId(authorization), requestedOwner);
    }

    private static void requireOwner(UUID authenticatedOwner, UUID requestedOwner) {
        if (!authenticatedOwner.equals(requestedOwner)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "owner does not match authenticated player");
        }
    }

    private List<UUID> authorizeDocuments(UUID ownerId, List<UUID> documentIds, DocumentType requiredType) {
        if (ownerId == null || documentIds == null || documentIds.isEmpty()
                || documentIds.stream().anyMatch(java.util.Objects::isNull)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "document scope must not be empty");
        }
        if (new HashSet<>(documentIds).size() != documentIds.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "document scope must not contain duplicates");
        }
        List<UUID> authorized = new java.util.ArrayList<>();
        for (UUID documentId : documentIds) {
            StoredRulebookRegistration registration = registrationRepository.findById(new RulebookId(documentId))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "document is not registered"));
            if (!registration.ownerPlayerId().value().equals(ownerId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "document does not belong to authenticated player");
            }
            if (registration.processingStatus() != ProcessingStatus.INDEXED) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "document is not indexed");
            }
            if (requiredType == null || registration.documentType() == requiredType) {
                authorized.add(documentId);
            }
        }
        if (authorized.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "document type is not allowed");
        }
        return List.copyOf(authorized);
    }

    private void requireIndexedVersion(UUID documentId, long extractionVersion) {
        if (documentId == null || extractionVersion <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "document scope is invalid");
        }
        StoredRulebookRegistration registration = registrationRepository.findById(new RulebookId(documentId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "document is not registered"));
        if (registration.version() != extractionVersion) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "document scope is not indexed at requested version");
        }
    }

    private void requirePublishedCatalogRulebook(UUID rulebookId) {
        if (rulebookId == null || !isPublishedCatalogScope(List.of(rulebookId))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "rulebook is not published in the shared catalog");
        }
    }

    /** Catalog scope is valid only when the published revision points at an indexed RULEBOOK. */
    private boolean isCatalogScope(List<UUID> documentIds) {
        return isPublishedCatalogScope(documentIds);
    }

    private boolean isPublishedCatalogScope(List<UUID> documentIds) {
        if (catalogRepository == null || documentIds == null || documentIds.isEmpty()
                || new HashSet<>(documentIds).size() != documentIds.size()) return false;
        try {
            Set<UUID> published = catalogRepository.findAll().stream()
                    .filter(item -> item.status() == com.dndmaster.ruleknowledge.domain.catalog.CatalogRevisionStatus.READY
                            && item.published())
                    .map(CatalogRulebookRevision::rulebookId)
                    .filter(java.util.Objects::nonNull)
                    .filter(id -> registrationRepository.findById(new RulebookId(id))
                            .filter(registration -> registration.processingStatus() == ProcessingStatus.INDEXED)
                            .filter(registration -> registration.documentType() == DocumentType.RULEBOOK)
                            .filter(registration -> registration.ownerPlayerId().value().equals(CATALOG_OWNER))
                            .filter(registration -> registration.version() > 0)
                            .isPresent())
                    .collect(java.util.stream.Collectors.toSet());
            return published.containsAll(documentIds);
        } catch (RuntimeException unavailable) {
            return false;
        }
    }

    private boolean isCatalogRegistration(StoredRulebookRegistration registration) {
        return registration.documentType() == DocumentType.RULEBOOK
                && isPublishedCatalogScope(List.of(registration.rulebookId().value()));
    }

    private static RulebookFormat resolveFormat(String filename) {
        if (filename == null) return RulebookFormat.PDF;
        return switch (filename.substring(filename.lastIndexOf('.') + 1).toLowerCase()) {
            case "pdf" -> RulebookFormat.PDF;
            case "docx" -> RulebookFormat.DOCX;
            case "txt" -> RulebookFormat.TXT;
            case "png", "jpg", "jpeg", "tif", "tiff", "bmp" -> RulebookFormat.IMAGE;
            default -> RulebookFormat.PDF;
        };
    }

    // Response records
    public record BatchUploadResponse(List<BatchUploadResult> documents) {}
    public record UploadDocumentRequest(String idempotencyKey, DocumentType documentType, String originalFilename) {}
    public record RulebookStatusResponse(
            UUID rulebookId, UUID knowledgeDocumentId, String status, DocumentType documentType,
            String originalFilename, String failureReason, long extractionVersion, List<String> warnings,
            DocumentProgressView progress, String candidateExtractionVersion,
            List<PreprocessingPageState> preprocessingPages, RetryabilityView retryability,
            List<PreprocessingReviewQuestion> reviewQuestions) {}
    public record RetryabilityView(boolean retryable, List<Integer> pages, List<String> diagnostics) {}
    public record RetryPagesRequest(String requestId, List<Integer> pages,
                                    java.util.Map<Integer, java.util.Map<String, Integer>> layoutSelections) {
        public RetryPagesRequest(String requestId, List<Integer> pages) {
            this(requestId, pages, java.util.Map.of());
        }
    }
    public record DocumentProgressView(
            String stage, int percent, Integer completedUnits, Integer totalUnits, String error) {}
    public record SourcePreviewResponse(
            UUID rulebookId, UUID knowledgeDocumentId, DocumentType documentType, String originalFilename,
            RulebookFormat format, String status, String content, long extractionVersion, List<String> warnings,
            List<PreviewSpanView> spans, List<PreviewAssetView> assets) {}
    public record RulebookSummary(
            UUID rulebookId, UUID knowledgeDocumentId, String status, String format,
            DocumentType documentType, String originalFilename, String failureReason, long extractionVersion, List<String> warnings,
            DocumentProgressView progress, List<PreprocessingReviewQuestion> reviewQuestions,
            List<PreprocessingPageState> preprocessingPages) {}
    public record PreprocessingReviewQuestion(int pageNumber, String question, List<ReviewChoice> choices) {}
    public record ReviewChoice(String id, String label) {}
    public record OwnedRulebooksResponse(UUID ownerId, List<RulebookSummary> rulebooks) {}
    public record OwnedIndexesResponse(UUID ownerId, List<?> indexes) {}
    public record OwnershipResponse(UUID rulebookId, UUID playerId, boolean owned) {}
    public record GameSystemDefinitionResponse(UUID rulebookId, long version, String definitionJson) {}
    public record GameSystemDefinitionRequest(long version, String definitionJson) {}
    public record RuleSetSaveRequest(List<UUID> knowledgeDocumentIds) {}
    public record EvidenceSearchRequest(UUID ownerId, List<UUID> rulebookIds, String situation, QueryIntent queryIntent, Integer limit,
                                        UUID sessionId, UUID scenarioPackageId, String stageKey, String actionIntent) {
        public EvidenceSearchRequest(UUID ownerId, List<UUID> rulebookIds, String situation, QueryIntent queryIntent, Integer limit) {
            this(ownerId, rulebookIds, situation, queryIntent, limit, null, null, null, null);
        }
    }
    public record EvidenceItem(UUID rulebookId, UUID chunkId, String locator, String excerpt, double score,
            String chapter, String section, ProvenanceView provenance, String citationKey) {}
    public record EvidenceSearchResponse(UUID ownerId, List<EvidenceItem> evidence) {}
    public record UnifiedEvidenceCandidateSearchRequest(
            UUID ownerId, UUID sessionId, UUID scenarioPackageId, String stageKey, String actionIntent,
            List<EvidenceCandidateScopeRequest> scope, List<String> activeLocators, String query, int denseLimit, int bm25Limit) {}
    public record EvidenceCandidateScopeRequest(UUID documentId, long extractionVersion, DocumentType documentType) {}
    public record UnifiedEvidenceCandidateSearchResponse(
            UUID ownerId, UUID sessionId, UUID scenarioPackageId, List<UnifiedEvidenceCandidateResponse> candidates) {}
    public record PreparationEvidenceCandidateSearchRequest(
            UUID ownerId, UUID scenarioSourceBundleId, List<EvidenceCandidateScopeRequest> scope,
            List<String> activeLocators, String query, int denseLimit, int bm25Limit) {}
    public record PreparationEvidenceCandidateSearchResponse(
            UUID ownerId, UUID scenarioSourceBundleId, List<UnifiedEvidenceCandidateResponse> candidates) {}
    public record UnifiedEvidenceCandidateResponse(
            UUID chunkId, UUID documentId, long extractionVersion, DocumentType documentType, String locator, String excerpt,
            SourceProvenance provenance, Integer denseRank, Integer bm25Rank, double rrfScore) {}
    public record EvidenceSearchErrorResponse(String code) {}
    public record StorySourceSearchRequest(
            UUID ownerId,
            List<StorySourceScopeRequest> documents,
            List<String> activeLocators,
            String situation,
            Integer limit,
            UUID sessionId,
            UUID scenarioPackageId,
            String stageKey,
            String actionIntent) {
        public StorySourceSearchRequest(UUID ownerId, List<StorySourceScopeRequest> documents,
                                        List<String> activeLocators, String situation, Integer limit) {
            this(ownerId, documents, activeLocators, situation, limit, null, null, null, null);
        }
    }
    public record StorySourceScopeRequest(UUID documentId, long extractionVersion) {}
    public record StorySourceSearchResponse(UUID ownerId, List<StorySourceEvidenceItem> evidence) {}
    public record StorySourceEvidenceItem(
            UUID knowledgeDocumentId, long extractionVersion, String locator, String excerpt, double score,
            ProvenanceView provenance, String citationKey) {}
    public record CharacterContextSearchRequest(
            UUID ownerId, List<CharacterContextScopeRequest> documents, String situation,
            Map<DocumentType, Double> thresholds, Integer tokenBudget) {}
    public record CharacterContextScopeRequest(UUID documentId, DocumentType documentType, long extractionVersion) {}
    public record CharacterContextSearchResponse(UUID ownerId, List<CharacterContextEvidenceItem> evidence) {}
    public record CharacterContextEvidenceItem(
            UUID knowledgeDocumentId, DocumentType documentType, long extractionVersion,
            String locator, String excerpt, double similarity) {}
    public record StorySourceContextResponse(
            UUID knowledgeDocumentId, long extractionVersion, String requestedLocator, List<StorySourceSpanItem> spans) {}
    public record StorySourceSpanItem(String locator, String excerpt, Integer pageNumber, String sourceMethod) {}
    public record PreviewSpanView(
            String kind,
            List<String> path,
            Integer pageNumber,
            BoundingBox bounds,
            int lineNumber,
            int startInclusive,
            int endExclusive,
            String text,
            String locator,
            String sourceMethod,
            Double confidence) {}
    public record PreviewAssetView(String kind, String locator, String contentType, Integer pageNumber) {}

    public record ProvenanceView(UUID documentId, long extractionVersion, int pageNumber, List<String> sectionPath,
            List<Double> bbox, String tableCell, String locator) {}

    private static ProvenanceView provenanceView(UUID documentId, long extractionVersion, SourceProvenance provenance) {
        return new ProvenanceView(documentId, extractionVersion, provenance.pageNumber(), provenance.sectionPath(),
                provenance.bbox(), provenance.tableCell(), provenance.originalLocator());
    }

    private static String runtimeCitationKey(String documentType, UUID documentId, long extractionVersion, String locator) {
        return documentType + ":" + documentId + ":" + extractionVersion + ":" + locator;
    }

    private static List<String> warningsFor(StoredRulebookRegistration registration) {
        List<String> warnings = new java.util.ArrayList<>();
        if (registration.failureCode() != null && !registration.failureCode().isBlank()) {
            warnings.add(registration.failureCode());
        }
        warnings.addAll(registration.missingLocations());
        warnings.addAll(registration.previewWarnings());
        return List.copyOf(warnings);
    }

    private static RetryabilityView retryabilityFor(StoredRulebookRegistration registration) {
        List<Integer> pages = registration.preprocessingPages().stream()
                .filter(page -> "NEEDS_REVIEW".equals(page.status()) && page.attempts() < 3)
                .map(PreprocessingPageState::pageNumber)
                .toList();
        List<String> diagnostics = registration.preprocessingPages().stream()
                .filter(page -> "NEEDS_REVIEW".equals(page.status()))
                .flatMap(page -> page.findings().stream())
                .distinct()
                .toList();
        return new RetryabilityView(registration.processingStatus() == ProcessingStatus.NEEDS_REVIEW && !pages.isEmpty(), pages, diagnostics);
    }

    private static List<PreprocessingReviewQuestion> reviewQuestionsFor(StoredRulebookRegistration registration) {
        return registration.preprocessingPages().stream()
                .filter(page -> "NEEDS_REVIEW".equals(page.status()) && page.attempts() < 3)
                .map(page -> new PreprocessingReviewQuestion(
                        page.pageNumber(),
                        "" + page.pageNumber() + "페이지의 자료를 다시 읽어 준비할까요?",
                        List.of(new ReviewChoice("RETRY", "다시 읽기"), new ReviewChoice("KEEP_REVIEW", "검토 상태 유지"))))
                .toList();
    }
}
