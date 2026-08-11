package com.dndmaster.ruleknowledge.api;

import com.dndmaster.ruleknowledge.application.catalog.CatalogRulebookRepository;
import com.dndmaster.ruleknowledge.application.catalog.CatalogRulebookRevision;
import com.dndmaster.ruleknowledge.application.pipeline.RulebookPipelineApplicationService;
import com.dndmaster.ruleknowledge.application.pipeline.UploadRulebookCommand;
import com.dndmaster.ruleknowledge.domain.catalog.CatalogRevisionStatus;
import com.dndmaster.ruleknowledge.domain.catalog.RulebookEdition;
import com.dndmaster.ruleknowledge.domain.rulebook.DocumentType;
import com.dndmaster.ruleknowledge.domain.rulebook.OwnerPlayerId;
import com.dndmaster.ruleknowledge.domain.rulebook.RulebookFormat;
import com.dndmaster.ruleknowledge.domain.rulebook.RulebookId;
import com.dndmaster.ruleknowledge.domain.rulebook.ProcessingStatus;
import com.dndmaster.ruleknowledge.application.registration.RulebookRegistrationRepository;
import java.io.IOException;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/backoffice/rulebook-catalog")
public final class RulebookCatalogBackofficeController {
    private static final UUID CATALOG_OWNER = UUID.fromString("00000000-0000-0000-0000-000000000005");
    private final CatalogRulebookRepository repository;
    private final RulebookPipelineApplicationService pipeline;
    private final RulebookRegistrationRepository registrations;
    private final Set<String> adminIds;

    public RulebookCatalogBackofficeController(CatalogRulebookRepository repository, RulebookPipelineApplicationService pipeline, RulebookRegistrationRepository registrations,
            @Value("${rule-knowledge.backoffice.admin-player-ids:}") String adminPlayerIds) {
        this.repository = repository; this.pipeline = pipeline; this.registrations = registrations;
        this.adminIds = Set.of(adminPlayerIds.split(","));
    }

    @PostMapping("/{catalogRevisionId}/publish")
    CatalogRulebookRevision publish(@RequestHeader("Authorization") String authorization, @org.springframework.web.bind.annotation.PathVariable UUID catalogRevisionId) {
        requireAdmin(authorization);
        CatalogRulebookRevision current = repository.findAll().stream().filter(item -> item.id().equals(catalogRevisionId)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "catalog revision not found"));
        if (current.rulebookId() == null || registrations.findById(new RulebookId(current.rulebookId()))
                .map(item -> item.processingStatus() == ProcessingStatus.INDEXED).orElse(false) == false) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "catalog revision is not indexed");
        }
        CatalogRulebookRevision published = new CatalogRulebookRevision(current.id(), current.edition(), current.displayName(), current.rulebookId(),
                current.revisionNumber(), CatalogRevisionStatus.READY, true, null, current.createdAt(), Instant.now());
        repository.publish(published);
        return published;
    }

    @PostMapping(consumes = "multipart/form-data")
    CatalogRulebookRevision upload(@RequestHeader("Authorization") String authorization,
            @RequestParam RulebookEdition edition, @RequestPart MultipartFile file) throws IOException {
        requireAdmin(authorization);
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".pdf")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "catalog rulebook must be a PDF");
        var result = pipeline.process(new UploadRulebookCommand("catalog:" + edition + ":" + UUID.randomUUID(),
                new OwnerPlayerId(CATALOG_OWNER), DocumentType.RULEBOOK, RulebookFormat.PDF, file.getBytes(), filename));
        Instant now = Instant.now();
        long revision = repository.findAll().stream().filter(item -> item.edition() == edition).mapToLong(CatalogRulebookRevision::revisionNumber).max().orElse(0) + 1;
        CatalogRulebookRevision catalog = new CatalogRulebookRevision(UUID.randomUUID(), edition,
                edition == RulebookEdition.DND_5E_2014 ? "D&D 5e (2014)" : "D&D 5.5e (2024)", result.rulebookId().value(),
                revision, CatalogRevisionStatus.QUEUED, false, null, now, now);
        repository.save(catalog);
        return catalog;
    }

    private void requireAdmin(String authorization) {
        String id = authorization != null && authorization.startsWith("Bearer ") ? authorization.substring(7) : "";
        if (!adminIds.contains(id)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "ADMIN role is required");
    }
}
