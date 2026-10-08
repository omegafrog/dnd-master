package com.dndmaster.ruleknowledge.api;

import com.dndmaster.ruleknowledge.application.catalog.CatalogRulebookRepository;
import com.dndmaster.ruleknowledge.application.catalog.CatalogRulebookRevision;
import com.dndmaster.ruleknowledge.application.auth.PlayerSessionLookupPort;
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
import com.dndmaster.ruleknowledge.application.registration.RulebookFileStorage;
import com.dndmaster.ruleknowledge.application.registration.StoredRulebookFile;
import com.dndmaster.ruleknowledge.application.registration.StoredRulebookRegistration;
import com.dndmaster.ruleknowledge.application.preprocessing.PreprocessingPageState;
import com.dndmaster.ruleknowledge.application.preprocessing.PreprocessingProcessException;
import com.dndmaster.ruleknowledge.application.definition.GameSystemDefinitionRepository;
import com.dndmaster.ruleknowledge.domain.definition.GameSystemDefinitionRevision;
import java.io.IOException;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
    private final RulebookFileStorage fileStorage;
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
                .map(BackofficeCatalogRulebookView::from)
                .toList();
    }

    public RulebookCatalogBackofficeController(CatalogRulebookRepository repository,
            RulebookPipelineApplicationService pipeline, RulebookRegistrationRepository registrations,
            GameSystemDefinitionRepository definitions, PlayerSessionLookupPort playerSessionLookup,
            @Value("${rule-knowledge.backoffice.admin-player-ids:}")
            String adminPlayerIds) {
        this(repository, pipeline, registrations, definitions, playerSessionLookup, null, adminPlayerIds);
    }

    @Autowired
    public RulebookCatalogBackofficeController(CatalogRulebookRepository repository,
            RulebookPipelineApplicationService pipeline, RulebookRegistrationRepository registrations,
            GameSystemDefinitionRepository definitions, PlayerSessionLookupPort playerSessionLookup,
            RulebookFileStorage fileStorage,
            @Value("${rule-knowledge.backoffice.admin-player-ids:}") String adminPlayerIds) {
        this.repository = repository; this.pipeline = pipeline; this.registrations = registrations; this.definitions = definitions;
        this.playerSessionLookup = playerSessionLookup;
        this.fileStorage = fileStorage;
        this.adminIds = Arrays.stream((adminPlayerIds == null ? "" : adminPlayerIds).split(","))
                .map(String::trim).filter(id -> !id.isBlank()).collect(Collectors.toUnmodifiableSet());
    }

    @GetMapping("/{catalogRevisionId}/review")
    CatalogReviewView review(@RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable UUID catalogRevisionId) {
        requireAdmin(authorization);
        StoredRulebookRegistration registration = catalogRegistration(catalogRevisionId);
        return new CatalogReviewView(registration.rulebookId().value(), registration.processingStatus().name(),
                registration.contentHash(), registration.candidateExtractionVersion(), registration.preprocessingPages());
    }

    @GetMapping(value = "/{catalogRevisionId}/source", produces = MediaType.APPLICATION_PDF_VALUE)
    ResponseEntity<byte[]> source(@RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable UUID catalogRevisionId) {
        requireAdmin(authorization);
        StoredRulebookRegistration registration = catalogRegistration(catalogRevisionId);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .body(fileStorage.read(new StoredRulebookFile(registration.storageKey())));
    }

    @PostMapping("/{catalogRevisionId}/retry-pages")
    CatalogReviewView retryPages(@RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable UUID catalogRevisionId, @RequestBody CatalogRetryRequest request) {
        UUID adminId = requireAdmin(authorization);
        StoredRulebookRegistration registration = catalogRegistration(catalogRevisionId);
        if (request == null || !java.util.Objects.equals(request.candidateExtractionVersion(), registration.candidateExtractionVersion())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "review candidate version changed");
        }
        if (!request.layoutSelections().isEmpty() && !request.confirmedAgainstSource()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "source confirmation is required");
        }
        try {
            pipeline.retryPages(registration.rulebookId(), request.requestId(), request.pages(),
                    request.layoutSelections(), request.candidateExtractionVersion(),
                    request.layoutSelections().isEmpty() ? null : adminId);
            return review(authorization, catalogRevisionId);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage(), exception);
        } catch (PreprocessingProcessException exception) {
            throw new ResponseStatusException("INVALID_LAYOUT_CANDIDATE".equals(exception.code())
                    ? HttpStatus.CONFLICT : HttpStatus.BAD_REQUEST, exception.code(), exception);
        }
    }

    private StoredRulebookRegistration catalogRegistration(UUID catalogRevisionId) {
        CatalogRulebookRevision catalog = repository.findAll().stream()
                .filter(item -> item.id().equals(catalogRevisionId)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "catalog revision not found"));
        if (catalog.rulebookId() == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "catalog document not found");
        return registrations.findById(new RulebookId(catalog.rulebookId()))
                .filter(item -> item.documentType() == DocumentType.RULEBOOK && item.ownerPlayerId().value().equals(CATALOG_OWNER))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "catalog document not found"));
    }

    public record CatalogReviewView(UUID rulebookId, String status, String contentHash,
            String candidateExtractionVersion, List<PreprocessingPageState> preprocessingPages) {}

    public record CatalogRetryRequest(String requestId, String candidateExtractionVersion, List<Integer> pages,
            Map<Integer, Map<String, Integer>> layoutSelections, boolean confirmedAgainstSource) {
        public CatalogRetryRequest {
            layoutSelections = layoutSelections == null ? Map.of() : Map.copyOf(layoutSelections);
        }
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

    private UUID requireAdmin(String authorization) {
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
        return playerId;
    }

    public record BackofficeCatalogRulebookView(
            String catalogRevisionId, String edition, String displayName, String rulebookId,
            long revisionNumber, String status, boolean published) {
        static BackofficeCatalogRulebookView from(CatalogRulebookRevision revision) {
            return new BackofficeCatalogRulebookView(
                    revision.id().toString(), revision.edition().name(), revision.displayName(),
                    revision.rulebookId() == null ? null : revision.rulebookId().toString(),
                    revision.revisionNumber(), revision.status().name(), revision.published());
        }
    }
}
