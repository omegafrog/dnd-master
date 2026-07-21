package com.dndmaster.ruleknowledge.api;

import com.dndmaster.ruleknowledge.application.registration.RulebookRegistrationApplicationService;
import com.dndmaster.ruleknowledge.domain.rulebook.OwnerPlayerId;
import com.dndmaster.ruleknowledge.domain.rulebook.RulebookFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping
public class RuleKnowledgeController {
    private final RulebookRegistrationApplicationService registrationService;

    public RuleKnowledgeController(RulebookRegistrationApplicationService registrationService) {
        this.registrationService = registrationService;
    }

    @PostMapping("/api/v1/rulebooks")
    ResponseEntity<Void> uploadRulebook(
            @RequestParam("file") MultipartFile file,
            @RequestParam("ownerPlayerId") UUID ownerPlayerId) throws Exception {
        RulebookFormat format = resolveFormat(file.getOriginalFilename());
        registrationService.uploadRulebook(
                new OwnerPlayerId(ownerPlayerId), format, file.getBytes());
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/api/v1/rulebooks/{rulebookId}")
    RulebookStatusResponse rulebookStatus(@PathVariable UUID rulebookId) {
        return new RulebookStatusResponse(rulebookId, "PENDING");
    }

    @GetMapping("/internal/v1/rulebooks")
    OwnedRulebooksResponse ownedRulebooks(@RequestParam UUID ownerId) {
        return new OwnedRulebooksResponse(ownerId);
    }

    @GetMapping("/internal/v1/rulebook-indexes")
    OwnedIndexesResponse ownedIndexes(@RequestParam UUID ownerId) {
        return new OwnedIndexesResponse(ownerId);
    }

    @GetMapping("/internal/v1/rulebooks/{rulebookId}/ownership")
    OwnershipResponse rulebookOwnership(@PathVariable UUID rulebookId, @RequestParam UUID playerId) {
        return new OwnershipResponse(rulebookId, playerId, true);
    }

    @PostMapping("/internal/v1/rule-evidence/search")
    EvidenceSearchResponse searchEvidence(@RequestBody EvidenceSearchRequest request) {
        return new EvidenceSearchResponse(request.ownerId(), List.of());
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

    public record RulebookStatusResponse(UUID rulebookId, String status) {}
    public record OwnedRulebooksResponse(UUID ownerId) {}
    public record OwnedIndexesResponse(UUID ownerId) {}
    public record OwnershipResponse(UUID rulebookId, UUID playerId, boolean owned) {}
    public record EvidenceSearchRequest(UUID ownerId, String situation) {}
    public record EvidenceSearchResponse(UUID ownerId, java.util.List<?> evidence) {}
}
