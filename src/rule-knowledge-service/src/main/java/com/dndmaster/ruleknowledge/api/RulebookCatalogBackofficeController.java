package com.dndmaster.ruleknowledge.api;

import com.dndmaster.ruleknowledge.application.catalog.CatalogRulebookRepository;
import com.dndmaster.ruleknowledge.application.catalog.CatalogRulebookRevision;
import com.dndmaster.ruleknowledge.application.auth.PlayerSessionLookupPort;
import com.dndmaster.ruleknowledge.application.pipeline.RulebookPipelineApplicationService;
import com.dndmaster.ruleknowledge.application.pipeline.RulebookProcessingResult;
import com.dndmaster.ruleknowledge.application.pipeline.UploadRulebookCommand;
import com.dndmaster.ruleknowledge.domain.catalog.CatalogRevisionStatus;
import com.dndmaster.ruleknowledge.domain.catalog.RulebookEdition;
import com.dndmaster.ruleknowledge.domain.rulebook.DocumentType;
import com.dndmaster.ruleknowledge.domain.rulebook.OwnerPlayerId;
import com.dndmaster.ruleknowledge.domain.rulebook.RulebookFormat;
import com.dndmaster.ruleknowledge.domain.rulebook.RulebookId;
import com.dndmaster.ruleknowledge.domain.rulebook.ProcessingStatus;
import com.dndmaster.ruleknowledge.application.registration.RulebookRegistrationRepository;
import com.dndmaster.ruleknowledge.application.definition.GameSystemDefinitionRepository;
import com.dndmaster.ruleknowledge.domain.definition.GameSystemDefinitionRevision;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
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
    private final GameSystemDefinitionRepository definitions;
    private final PlayerSessionLookupPort playerSessionLookup;
    private final Set<String> adminIds;

    public RulebookCatalogBackofficeController(CatalogRulebookRepository repository, RulebookPipelineApplicationService pipeline, RulebookRegistrationRepository registrations,
            GameSystemDefinitionRepository definitions,
            @Value("${rule-knowledge.backoffice.admin-player-ids:}") String adminPlayerIds) {
        this(repository, pipeline, registrations, definitions, null, adminPlayerIds);
    }

    @GetMapping
    java.util.List<BackofficeCatalogRulebookView> list(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        requireAdmin(authorization);
        return repository.findAll().stream()
                .map(revision -> BackofficeCatalogRulebookView.from(revision, registrations))
                .toList();
    }

    @Autowired
    public RulebookCatalogBackofficeController(CatalogRulebookRepository repository,
            RulebookPipelineApplicationService pipeline, RulebookRegistrationRepository registrations,
            GameSystemDefinitionRepository definitions, PlayerSessionLookupPort playerSessionLookup,
            @Value("${rule-knowledge.backoffice.admin-player-ids:}")
            String adminPlayerIds) {
        this.repository = repository; this.pipeline = pipeline; this.registrations = registrations; this.definitions = definitions;
        this.playerSessionLookup = playerSessionLookup;
        this.adminIds = Arrays.stream((adminPlayerIds == null ? "" : adminPlayerIds).split(","))
                .map(String::trim).filter(id -> !id.isBlank()).collect(Collectors.toUnmodifiableSet());
    }

    @PostMapping("/{catalogRevisionId}/publish")
    CatalogRulebookRevision publish(@RequestHeader(value = "Authorization", required = false) String authorization,
            @org.springframework.web.bind.annotation.PathVariable UUID catalogRevisionId) {
        requireAdmin(authorization);
        CatalogRulebookRevision current = repository.findAll().stream().filter(item -> item.id().equals(catalogRevisionId)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "catalog revision not found"));
        if (current.rulebookId() == null || registrations.findById(new RulebookId(current.rulebookId()))
                .map(item -> item.processingStatus() == ProcessingStatus.INDEXED
                        && item.documentType() == DocumentType.RULEBOOK
                        && item.ownerPlayerId().value().equals(CATALOG_OWNER) && item.version() > 0)
                .orElse(false) == false) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "catalog revision is not indexed");
        }
        CatalogRulebookRevision published = new CatalogRulebookRevision(current.id(), current.edition(), current.displayName(), current.rulebookId(),
                current.revisionNumber(), CatalogRevisionStatus.READY, true, null, current.createdAt(), Instant.now());
        repository.publish(published);
        ensureSystemDefinition(published);
        return published;
    }

    @PostMapping("/{catalogRevisionId}/retry")
    RulebookProcessingResult retry(@RequestHeader(value = "Authorization", required = false) String authorization,
            @org.springframework.web.bind.annotation.PathVariable UUID catalogRevisionId) {
        requireAdmin(authorization);
        CatalogRulebookRevision current = requireCatalogRevision(catalogRevisionId);
        try {
            return pipeline.retry(new RulebookId(current.rulebookId()));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage(), exception);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage(), exception);
        }
    }

    @PostMapping("/{catalogRevisionId}/retry-pages")
    RulebookProcessingResult retryPages(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @org.springframework.web.bind.annotation.PathVariable UUID catalogRevisionId,
            @org.springframework.web.bind.annotation.RequestBody RetryPagesRequest request) {
        requireAdmin(authorization);
        CatalogRulebookRevision current = requireCatalogRevision(catalogRevisionId);
        try {
            return pipeline.retryPages(new RulebookId(current.rulebookId()),
                    request == null ? null : request.requestId(),
                    request == null ? null : request.pages(),
                    request == null ? Map.of() : request.layoutSelections());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage(), exception);
        }
    }

    private CatalogRulebookRevision requireCatalogRevision(UUID catalogRevisionId) {
        CatalogRulebookRevision current = repository.findAll().stream()
                .filter(item -> item.id().equals(catalogRevisionId)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "catalog revision not found"));
        if (current.rulebookId() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "catalog revision document not found");
        }
        var registration = registrations.findById(new RulebookId(current.rulebookId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "catalog revision document not found"));
        if (!registration.ownerPlayerId().value().equals(CATALOG_OWNER)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "catalog revision document does not belong to catalog owner");
        }
        return current;
    }

    private void ensureSystemDefinition(CatalogRulebookRevision catalog) {
        if (catalog.edition() != RulebookEdition.DND_5E_2014 || catalog.rulebookId() == null
                || definitions.findPublished(catalog.rulebookId()).isPresent()) return;
        definitions.save(GameSystemDefinitionRevision.draft(catalog.rulebookId(), 1, """
                {"edition":"DND_5E_2014","time":{"secondsPerTurn":6},
                 "characterCreation":{"levelRange":{"min":1,"max":20},
                 "abilityScores":["strength","dexterity","constitution","intelligence","wisdom","charisma"],
                 "proficiencyBonus":{"level1":2}},
                 "combat":{"initiative":"dexterity","actionEconomy":"action_bonus_reaction"}}
                """).publish());
    }

    @PostMapping(consumes = "multipart/form-data")
    CatalogRulebookRevision upload(@RequestHeader(value = "Authorization", required = false) String authorization,
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
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Bearer authorization is required");
        }
        String token = authorization.substring("Bearer ".length());
        if (token.isBlank() || playerSessionLookup == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Bearer authorization is invalid");
        }
        UUID playerId = playerSessionLookup.resolvePlayerId(token)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Bearer authorization is invalid"));
        if (!adminIds.contains(playerId.toString())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "ADMIN role is required");
        }
    }

    public record BackofficeCatalogRulebookView(
            String catalogRevisionId, String edition, String displayName, String rulebookId,
            long revisionNumber, String status, String processingStatus, boolean published) {
        static BackofficeCatalogRulebookView from(CatalogRulebookRevision revision,
                RulebookRegistrationRepository registrations) {
            return new BackofficeCatalogRulebookView(
                    revision.id().toString(), revision.edition().name(), revision.displayName(),
                    revision.rulebookId() == null ? null : revision.rulebookId().toString(),
                    revision.revisionNumber(), revision.status().name(),
                    revision.rulebookId() == null ? null : registrations.findById(new RulebookId(revision.rulebookId()))
                            .map(item -> item.processingStatus().name()).orElse(null),
                    revision.published());
        }
    }

    public record RetryPagesRequest(String requestId, List<Integer> pages,
            Map<Integer, Map<String, Integer>> layoutSelections) {
        public RetryPagesRequest(String requestId, List<Integer> pages) {
            this(requestId, pages, Map.of());
        }
    }
}
