package com.dndmaster.ruleknowledge.api;

import com.dndmaster.ruleknowledge.application.pipeline.RulebookPipelineApplicationService;
import com.dndmaster.ruleknowledge.application.pipeline.RulebookProcessingResult;
import com.dndmaster.ruleknowledge.application.pipeline.UploadRulebookCommand;
import com.dndmaster.ruleknowledge.application.registration.RulebookRegistrationRepository;
import com.dndmaster.ruleknowledge.application.registration.StoredRulebookRegistration;
import com.dndmaster.ruleknowledge.application.search.RuleEvidenceResult;
import com.dndmaster.ruleknowledge.application.search.RuleEvidenceSearchApplicationService;
import com.dndmaster.ruleknowledge.application.search.SearchRuleEvidenceQuery;
import com.dndmaster.ruleknowledge.domain.rulebook.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping
public class RuleKnowledgeController {
    private final RulebookPipelineApplicationService pipelineService;
    private final RulebookRegistrationRepository registrationRepository;
    private final RuleEvidenceSearchApplicationService evidenceSearchService;

    public RuleKnowledgeController(
            RulebookPipelineApplicationService pipelineService,
            RulebookRegistrationRepository registrationRepository,
            RuleEvidenceSearchApplicationService evidenceSearchService) {
        this.pipelineService = pipelineService;
        this.registrationRepository = registrationRepository;
        this.evidenceSearchService = evidenceSearchService;
    }

    @PostMapping("/api/v1/rulebooks")
    ResponseEntity<AsyncStatusResponse> uploadRulebook(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestParam("file") MultipartFile file,
            @RequestParam("ownerPlayerId") UUID ownerPlayerId) throws Exception {
        RulebookFormat format = resolveFormat(file.getOriginalFilename());
        UploadRulebookCommand command = new UploadRulebookCommand(
                idempotencyKey,
                new OwnerPlayerId(ownerPlayerId),
                format,
                file.getBytes());
        RulebookProcessingResult result = pipelineService.process(command);
        return ResponseEntity.accepted().body(new AsyncStatusResponse(
                result.rulebookId().value(),
                mapStatus(result.status()),
                result.warnings()));
    }

    @GetMapping("/api/v1/rulebooks/{rulebookId}")
    RulebookStatusResponse rulebookStatus(@PathVariable UUID rulebookId) {
        return registrationRepository.findById(new RulebookId(rulebookId))
                .map(r -> new RulebookStatusResponse(rulebookId, r.processingStatus().name()))
                .orElse(new RulebookStatusResponse(rulebookId, "NOT_FOUND"));
    }

    @GetMapping("/internal/v1/rulebooks")
    OwnedRulebooksResponse ownedRulebooks(@RequestParam UUID ownerId) {
        List<StoredRulebookRegistration> registrations = registrationRepository.findByOwner(new OwnerPlayerId(ownerId));
        List<RulebookSummary> summaries = registrations.stream()
                .map(r -> new RulebookSummary(r.rulebookId().value(), r.processingStatus().name(), r.format().name()))
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
                        r.score()))
                .toList();
        return new EvidenceSearchResponse(request.ownerId(), evidence);
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

    private static String mapStatus(ProcessingStatus status) {
        return switch (status) {
            case UPLOADED -> "EXTRACTING";
            case EXTRACTED -> "INDEXING";
            case PARTIAL_AWAITING_CONFIRMATION -> "PARTIAL";
            case PARTIAL_CONFIRMED -> "INDEXING";
            case REJECTED -> "FAILED";
        };
    }

    // Response records
    public record AsyncStatusResponse(UUID resourceId, String status, List<String> warnings) {}
    public record RulebookStatusResponse(UUID rulebookId, String status) {}
    public record RulebookSummary(UUID rulebookId, String status, String format) {}
    public record OwnedRulebooksResponse(UUID ownerId, List<RulebookSummary> rulebooks) {}
    public record OwnedIndexesResponse(UUID ownerId, List<?> indexes) {}
    public record OwnershipResponse(UUID rulebookId, UUID playerId, boolean owned) {}
    public record EvidenceSearchRequest(UUID ownerId, List<UUID> rulebookIds, String situation, Integer limit) {}
    public record EvidenceItem(UUID rulebookId, UUID chunkId, String locator, String excerpt, double score) {}
    public record EvidenceSearchResponse(UUID ownerId, List<EvidenceItem> evidence) {}
}
