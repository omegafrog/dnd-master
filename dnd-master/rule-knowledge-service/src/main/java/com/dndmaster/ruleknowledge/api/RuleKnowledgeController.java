package com.dndmaster.ruleknowledge.api;

import com.dndmaster.ruleknowledge.application.pipeline.BatchRulebookUploadApplicationService;
import com.dndmaster.ruleknowledge.application.pipeline.BatchRulebookUploadApplicationService.BatchUploadItem;
import com.dndmaster.ruleknowledge.application.pipeline.BatchRulebookUploadApplicationService.BatchUploadResult;
import com.dndmaster.ruleknowledge.application.pipeline.RulebookPipelineApplicationService;
import com.dndmaster.ruleknowledge.application.registration.RulebookRegistrationRepository;
import com.dndmaster.ruleknowledge.application.registration.StoredRulebookRegistration;
import com.dndmaster.ruleknowledge.application.search.RuleEvidenceResult;
import com.dndmaster.ruleknowledge.application.search.RuleEvidenceSearchApplicationService;
import com.dndmaster.ruleknowledge.application.search.SearchRuleEvidenceQuery;
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
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping
public class RuleKnowledgeController {
    private final BatchRulebookUploadApplicationService batchUploadService;
    private final RulebookPipelineApplicationService pipelineService;
    private final RulebookRegistrationRepository registrationRepository;
    private final RuleEvidenceSearchApplicationService evidenceSearchService;
    private final ObjectMapper objectMapper;

    public RuleKnowledgeController(
            RulebookPipelineApplicationService pipelineService,
            RulebookRegistrationRepository registrationRepository,
            RuleEvidenceSearchApplicationService evidenceSearchService,
            ObjectMapper objectMapper) {
        this.pipelineService = pipelineService;
        this.batchUploadService = new BatchRulebookUploadApplicationService(pipelineService);
        this.registrationRepository = registrationRepository;
        this.evidenceSearchService = evidenceSearchService;
        this.objectMapper = objectMapper;
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
                        r.failureCode()))
                .orElse(new RulebookStatusResponse(rulebookId, null, "NOT_FOUND", null, null, null));
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

    @GetMapping("/internal/v1/rulebooks")
    OwnedRulebooksResponse ownedRulebooks(@RequestParam UUID ownerId) {
        List<StoredRulebookRegistration> registrations = registrationRepository.findByOwner(new OwnerPlayerId(ownerId));
        List<RulebookSummary> summaries = registrations.stream()
                .map(r -> new RulebookSummary(
                        r.rulebookId().value(), r.knowledgeDocumentId().value(), r.processingStatus().name(),
                        r.format().name(), r.documentType(), r.originalFilename(), r.failureCode()))
                .toList();
        return new OwnedRulebooksResponse(ownerId, summaries);
    }

    @GetMapping("/internal/v1/rulebook-indexes")
    OwnedIndexesResponse ownedIndexes(@RequestParam UUID ownerId) {
        return new OwnedIndexesResponse(ownerId, List.of());
    }

    @GetMapping("/internal/v1/rulebooks/{rulebookId}/ownership")
    OwnershipResponse rulebookOwnership(@PathVariable UUID rulebookId, @RequestParam UUID playerId) {
        boolean owned = registrationRepository.findById(new RulebookId(rulebookId))
                .map(r -> r.ownerPlayerId().value().equals(playerId))
                .orElse(false);
        return new OwnershipResponse(rulebookId, playerId, owned);
    }

    @PostMapping("/internal/v1/rule-evidence/search")
    EvidenceSearchResponse searchEvidence(@RequestBody EvidenceSearchRequest request) {
        List<RulebookId> rulebookIds = request.rulebookIds().stream()
                .map(RulebookId::new)
                .toList();
        SearchRuleEvidenceQuery query = new SearchRuleEvidenceQuery(
                new OwnerPlayerId(request.ownerId()),
                rulebookIds,
                request.situation(),
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
                        r.section()))
                .toList();
        return new EvidenceSearchResponse(request.ownerId(), evidence);
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

    private static RulebookFormat resolveFormat(String filename) {
        if (filename == null) return RulebookFormat.PDF;
        return switch (filename.substring(filename.lastIndexOf('.') + 1).toLowerCase()) {
            case "pdf" -> RulebookFormat.PDF;
            case "docx" -> RulebookFormat.DOCX;
            case "txt" -> RulebookFormat.TXT;
            default -> RulebookFormat.PDF;
        };
    }

    // Response records
    public record BatchUploadResponse(List<BatchUploadResult> documents) {}
    public record UploadDocumentRequest(String idempotencyKey, DocumentType documentType, String originalFilename) {}
    public record RulebookStatusResponse(
            UUID rulebookId, UUID knowledgeDocumentId, String status, DocumentType documentType, String originalFilename, String failureReason) {}
    public record RulebookSummary(
            UUID rulebookId, UUID knowledgeDocumentId, String status, String format,
            DocumentType documentType, String originalFilename, String failureReason) {}
    public record OwnedRulebooksResponse(UUID ownerId, List<RulebookSummary> rulebooks) {}
    public record OwnedIndexesResponse(UUID ownerId, List<?> indexes) {}
    public record OwnershipResponse(UUID rulebookId, UUID playerId, boolean owned) {}
    public record EvidenceSearchRequest(UUID ownerId, List<UUID> rulebookIds, String situation, Integer limit) {}
    public record EvidenceItem(UUID rulebookId, UUID chunkId, String locator, String excerpt, double score, String chapter, String section) {}
    public record EvidenceSearchResponse(UUID ownerId, List<EvidenceItem> evidence) {}
}
