package com.dndmaster.adventure.api;

import com.dndmaster.adventure.application.scenario.BundleDocumentDraft;
import com.dndmaster.adventure.application.scenario.ScenarioBundleApplicationService;
import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import com.dndmaster.adventure.domain.scenario.OwnerPlayerId;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleDocumentRole;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleId;
import com.dndmaster.adventure.domain.scenario.ScenarioSourceBundle;
import com.dndmaster.adventure.domain.scenario.RulebookEdition;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping
public class ScenarioBundleController {
    private final ScenarioBundleApplicationService service;
    private final AuthenticatedPlayerResolver playerResolver;

    public ScenarioBundleController(ScenarioBundleApplicationService service, AuthenticatedPlayerResolver playerResolver) {
        this.service = service;
        this.playerResolver = playerResolver;
    }

    @PostMapping("/api/v1/adventures/scenario-bundles")
    ScenarioBundleResponse createBundle(
            @RequestBody ScenarioBundleRequest request) {
        OwnerPlayerId owner = new OwnerPlayerId(playerResolver.playerId());
        authorize(owner, request.playerId());
        List<BundleDocumentDraft> documents = request.documents().stream().map(draft ->
                new BundleDocumentDraft(new KnowledgeDocumentId(draft.knowledgeDocumentId()), draft.role())).toList();
        ScenarioSourceBundle bundle = request.name() == null && request.rulebookEdition() == null
                ? service.createBundle(owner, documents)
                : service.createBundle(owner, request.name(), request.rulebookEdition(), documents);
        return ScenarioBundleResponse.from(bundle);
    }

    @PostMapping("/api/v1/adventures/scenario-bundles/{bundleId}/revisions")
    ScenarioBundleResponse reviseBundle(
            @PathVariable UUID bundleId,
            @RequestBody ScenarioBundleRequest request) {
        OwnerPlayerId owner = new OwnerPlayerId(playerResolver.playerId());
        authorize(owner, request.playerId());
        List<BundleDocumentDraft> documents = request.documents().stream()
                        .map(draft -> new BundleDocumentDraft(new KnowledgeDocumentId(draft.knowledgeDocumentId()), draft.role()))
                        .toList();
        ScenarioSourceBundle bundle = request.name() == null && request.rulebookEdition() == null
                ? service.reviseBundle(new ScenarioBundleId(bundleId), owner, documents)
                : service.reviseBundle(new ScenarioBundleId(bundleId), owner, request.name(), request.rulebookEdition(), documents);
        return ScenarioBundleResponse.from(bundle);
    }

    @GetMapping("/api/v1/adventures/scenario-bundles/{bundleId}")
    ScenarioBundleResponse readBundle(
            @PathVariable UUID bundleId) {
        OwnerPlayerId owner = new OwnerPlayerId(playerResolver.playerId());
        return ScenarioBundleResponse.from(service.readBundle(new ScenarioBundleId(bundleId), owner));
    }

    @GetMapping("/api/v1/adventures/scenario-bundles")
    List<ScenarioBundleResponse> listBundles() {
        return service.listBundles(new OwnerPlayerId(playerResolver.playerId())).stream()
                .map(ScenarioBundleResponse::from)
                .toList();
    }

    @DeleteMapping("/api/v1/adventures/scenario-bundles/{bundleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteBundle(@PathVariable UUID bundleId) {
        service.deleteBundle(new ScenarioBundleId(bundleId), new OwnerPlayerId(playerResolver.playerId()));
    }

    private static void authorize(OwnerPlayerId owner, UUID requestPlayerId) {
        if (!owner.value().equals(requestPlayerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "playerId must match Authorization");
        }
    }

    public record ScenarioBundleRequest(UUID playerId, String name, RulebookEdition rulebookEdition, List<ScenarioBundleDocumentRequest> documents) {}

    public record ScenarioBundleDocumentRequest(UUID knowledgeDocumentId, ScenarioBundleDocumentRole role) {}

    public record ScenarioBundleDocumentResponse(
            UUID knowledgeDocumentId,
            ScenarioBundleDocumentRole role,
            String originalFilename,
            String documentType,
            String status,
            long extractionVersion) {}

    public record ScenarioBundleResponse(
            UUID bundleId,
            UUID ownerPlayerId,
            String name,
            RulebookEdition rulebookEdition,
            long currentRevision,
            List<ScenarioBundleDocumentResponse> documents) {
        static ScenarioBundleResponse from(ScenarioSourceBundle bundle) {
            return new ScenarioBundleResponse(
                    bundle.id().value(),
                    bundle.ownerPlayerId().value(),
                    bundle.name(),
                    bundle.rulebookEdition(),
                    bundle.currentRevision().revision(),
                    bundle.currentRevision().documents().stream()
                            .map(document -> new ScenarioBundleDocumentResponse(
                                    document.knowledgeDocumentId().value(),
                                    document.role(),
                                    document.originalFilename(),
                                    document.documentType(),
                                    document.status().name(),
                                    document.extractionVersion()))
                            .toList());
        }
    }
}
