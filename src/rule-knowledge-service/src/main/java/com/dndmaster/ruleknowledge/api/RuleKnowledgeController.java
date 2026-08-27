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

    public RuleKnowledgeController(
            RulebookPipelineApplicationService pipelineService,
            RulebookRegistrationRepository registrationRepository,
            RuleEvidenceSearchApplicationService evidenceSearchService,
            ObjectMapper objectMapper) {
        this(pipelineService, registrationRepository, evidenceSearchService, null, null, null, objectMapper);
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
                characterContextSearchService, indexRepository, objectMapper, definitionRepository, internalToken, null);
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
            @RequestPart("files") List<MultipartFile> files) throws IOException {
        List<UploadDocumentRequest> uploadDocuments = parseDocuments(documents.getBytes());
        if (uploadDocuments.size() != files.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "documents and files must have the same size");
        }
        List<BatchUploadItem> items = new java.util.ArrayList<>(files.size());
        for (int index = 0; index < files.size(); index++) {
            MultipartFile file = files.get(index);
            UploadDocumentRequest document = uploadDocuments.get(index);
            if (document.documentType() != DocumentType.STORYBOOK) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "user uploads accept STORYBOOK only; rulebooks are selected from the shared catalog");
            }
            String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : document.originalFilename();
            items.add(new BatchUploadItem(
                    document.idempotencyKey(),
                    new OwnerPlayerId(ownerPlayerId),
                    document.documentType(),
                    resolveFormat(originalFilename),
                    originalFilename,
                    file.getBytes()));
        }
        List<BatchUploadResult> results = batchUploadService.process(items);
        return ResponseEntity.accepted().body(new BatchUploadResponse(results));
    }

    @GetMapping("/api/v1/rulebooks/{rulebookId}")
    RulebookStatusResponse rulebookStatus(@PathVariable UUID rulebookId) {
        return registrationRepository.findById(new RulebookId(rulebookId))
                .map(r -> new RulebookStatusResponse(
                        rulebookId,
                        r.knowledgeDocumentId().value(),
                        r.processingStatus().name(),
                        r.documentType(),
                        r.originalFilename(),
                        r.failureCode(),
                        r.version(),
                        warningsFor(r), progressFor(r), r.candidateExtractionVersion(), r.preprocessingPages(), retryabilityFor(r)))
                .orElse(new RulebookStatusResponse(rulebookId, null, "NOT_FOUND", null, null, null, 0L, List.of(), null, null, List.of(),
                        new RetryabilityView(false, List.of(), List.of("DOCUMENT_NOT_FOUND"))));
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
    ResponseEntity<SourcePreviewResponse> sourcePreview(@PathVariable UUID rulebookId) {
        StoredRulebookRegistration registration = registrationRepository.findById(new RulebookId(rulebookId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "knowledge document not found"));
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
        if (registrationRepository.findById(new RulebookId(rulebookId)).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "rulebook not found");
        }
        GameSystemDefinitionRevision revision = GameSystemDefinitionRevision.draft(
                rulebookId, request.version(), request.definitionJson()).publish();
        definitionRepository.save(revision);
        return new GameSystemDefinitionResponse(rulebookId, revision.version(), revision.definitionJson());
    }

    private void requireInternalToken(String token) {
        if (internalToken.isBlank() || !internalToken.equals(token)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "invalid internal token");
        }
    }

    @PostMapping("/api/v1/rulebooks/{rulebookId}/retry")
    RulebookStatusResponse retryRulebook(@PathVariable UUID rulebookId) {
        try {
            pipelineService.retry(new RulebookId(rulebookId));
            return rulebookStatus(rulebookId);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage(), exception);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage(), exception);
        }
    }

    @PostMapping("/api/v1/rulebooks/{rulebookId}/retry-pages")
    RulebookStatusResponse retryPages(
            @PathVariable UUID rulebookId,
            @RequestHeader("Authorization") String authorization,
            @RequestBody RetryPagesRequest request) {
        StoredRulebookRegistration registration = registrationRepository.findById(new RulebookId(rulebookId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "knowledge document not found"));
        requireOwner(extractPlayerId(authorization), registration.ownerPlayerId().value());
        try {
            pipelineService.retryPages(new RulebookId(rulebookId), request == null ? null : request.requestId(),
                    request == null ? null : request.pages());
            return rulebookStatus(rulebookId);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage(), exception);
        }
    }

    @DeleteMapping("/api/v1/rulebooks/{rulebookId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteRulebook(@PathVariable UUID rulebookId, @RequestHeader("Authorization") String authorization) {
        try {
            pipelineService.delete(new RulebookId(rulebookId), new OwnerPlayerId(extractPlayerId(authorization)));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage(), exception);
        } catch (SecurityException exception) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, exception.getMessage(), exception);
        }
    }

    @PostMapping("/api/v1/rulebooks/rule-set")
    ResponseEntity<Void> saveRuleSet(
            @RequestHeader("Authorization") String authorization,
            @RequestBody RuleSetSaveRequest request) {
        UUID ownerId = extractPlayerId(authorization);
        List<UUID> knowledgeDocumentIds = request.knowledgeDocumentIds();
        if (knowledgeDocumentIds == null || knowledgeDocumentIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "knowledgeDocumentIds must not be empty");
        }
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
    OwnedRulebooksResponse ownedRulebooks(@RequestParam UUID ownerId) {
        List<StoredRulebookRegistration> registrations = registrationRepository.findByOwner(new OwnerPlayerId(ownerId));
        List<RulebookSummary> summaries = registrations.stream()
                .map(r -> new RulebookSummary(
                        r.rulebookId().value(), r.knowledgeDocumentId().value(), r.processingStatus().name(),
                        r.format().name(), r.documentType(), r.originalFilename(), r.failureCode(),
                        r.version(), warningsFor(r), progressFor(r)))
                .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
        if (catalogRepository != null) {
            catalogRepository.findAll().stream()
                    .filter(item -> item.status() == com.dndmaster.ruleknowledge.domain.catalog.CatalogRevisionStatus.READY
                            && item.published() && item.rulebookId() != null)
                    .forEach(item -> {
                        long extractionVersion = registrationRepository.findById(new RulebookId(item.rulebookId()))
                                .map(StoredRulebookRegistration::version).orElse(0L);
                        if (extractionVersion > 0 && summaries.stream().noneMatch(existing -> existing.knowledgeDocumentId().equals(item.rulebookId()))) {
                            summaries.add(new RulebookSummary(item.rulebookId(), item.rulebookId(), "INDEXED", "PDF", DocumentType.RULEBOOK,
                                    item.displayName(), null, extractionVersion, List.of(), new DocumentProgressView("READY", 100, null, null, null)));
                        }
                    });
        }
        return new OwnedRulebooksResponse(ownerId, summaries);
    }

    @GetMapping("/internal/v1/rulebook-indexes")
    OwnedIndexesResponse ownedIndexes(@RequestParam UUID ownerId) {
        return new OwnedIndexesResponse(ownerId, List.of());
    }

    @GetMapping("/internal/v1/rulebooks/{rulebookId}/ownership")
    OwnershipResponse rulebookOwnership(@PathVariable UUID rulebookId, @RequestParam UUID playerId) {
        boolean owned = isCatalogScope(List.of(rulebookId)) || registrationRepository.findById(new RulebookId(rulebookId))
                .map(r -> r.ownerPlayerId().value().equals(playerId))
                .orElse(false);
        return new OwnershipResponse(rulebookId, playerId, owned);
    }

    @PostMapping("/internal/v1/rule-evidence/search")
    EvidenceSearchResponse searchEvidence(
            @RequestHeader("Authorization") String authorization,
            @RequestBody EvidenceSearchRequest request) {
        UUID authenticatedOwner = extractPlayerId(authorization);
        requireOwner(authenticatedOwner, request.ownerId());
        boolean catalogScope = isCatalogScope(request.rulebookIds());
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
            @RequestHeader("Authorization") String authorization,
            @RequestBody StorySourceSearchRequest request) {
        if (storySourceSearchService == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "story source search is not configured");
        }
        UUID authenticatedOwner = extractPlayerId(authorization);
        requireOwner(authenticatedOwner, request.ownerId());
        List<UUID> authorizedStorybookIds = authorizeDocuments(request.ownerId(), request.documents().stream()
                .map(StorySourceScopeRequest::documentId).toList(), DocumentType.STORYBOOK);
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
            @RequestHeader("Authorization") String authorization,
            @RequestBody CharacterContextSearchRequest request) {
        if (characterContextSearchService == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "character context search is not configured");
        }
        requireOwner(extractPlayerId(authorization), request.ownerId());
        if (request.documents() == null || request.documents().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "document scope must not be empty");
        }
        Map<DocumentType, List<CharacterContextDocumentScope>> scope = new java.util.EnumMap<>(DocumentType.class);
        Set<String> seen = new HashSet<>();
        boolean catalogScope = isCatalogScope(request.documents().stream().map(CharacterContextScopeRequest::documentId).toList());
        for (CharacterContextScopeRequest document : request.documents()) {
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
            @RequestHeader("Authorization") String authorization,
            @RequestParam UUID ownerId,
            @RequestParam long extractionVersion,
            @RequestParam String locator) {
        requireOwner(extractPlayerId(authorization), ownerId);
        StoredRulebookRegistration registration = registrationRepository.findById(new RulebookId(documentId))
                .filter(candidate -> candidate.ownerPlayerId().value().equals(ownerId))
                .filter(candidate -> candidate.documentType() == DocumentType.STORYBOOK)
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

    private static UUID extractPlayerId(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Bearer authorization is required");
        }
        try {
            return UUID.fromString(authorization.substring("Bearer ".length()));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Bearer authorization is invalid", exception);
        }
    }

    private static void requireOwner(UUID authenticatedOwner, UUID requestedOwner) {
        if (!authenticatedOwner.equals(requestedOwner)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "owner does not match authenticated player");
        }
    }

    private List<UUID> authorizeDocuments(UUID ownerId, List<UUID> documentIds, DocumentType requiredType) {
        if (documentIds == null || documentIds.isEmpty()) {
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

    /** Published revisions are selectable; any READY revision remains readable to preserve existing adventure pins. */
    private boolean isCatalogScope(List<UUID> documentIds) {
        if (catalogRepository == null || documentIds == null || documentIds.isEmpty()
                || new HashSet<>(documentIds).size() != documentIds.size()) return false;
        try {
            Set<UUID> published = catalogRepository.findAll().stream()
                    .filter(item -> item.status() == com.dndmaster.ruleknowledge.domain.catalog.CatalogRevisionStatus.READY)
                    .map(CatalogRulebookRevision::rulebookId)
                    .filter(java.util.Objects::nonNull)
                    .collect(java.util.stream.Collectors.toSet());
            return published.containsAll(documentIds);
        } catch (RuntimeException unavailable) {
            // Legacy installations may not have run the catalog migration yet; owned documents still work.
            return false;
        }
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
            List<PreprocessingPageState> preprocessingPages, RetryabilityView retryability) {}
    public record RetryabilityView(boolean retryable, List<Integer> pages, List<String> diagnostics) {}
    public record RetryPagesRequest(String requestId, List<Integer> pages) {}
    public record DocumentProgressView(
            String stage, int percent, Integer completedUnits, Integer totalUnits, String error) {}
    public record SourcePreviewResponse(
            UUID rulebookId, UUID knowledgeDocumentId, DocumentType documentType, String originalFilename,
            RulebookFormat format, String status, String content, long extractionVersion, List<String> warnings,
            List<PreviewSpanView> spans, List<PreviewAssetView> assets) {}
    public record RulebookSummary(
            UUID rulebookId, UUID knowledgeDocumentId, String status, String format,
            DocumentType documentType, String originalFilename, String failureReason, long extractionVersion, List<String> warnings,
            DocumentProgressView progress) {}
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
}
