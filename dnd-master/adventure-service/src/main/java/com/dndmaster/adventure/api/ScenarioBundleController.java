package com.dndmaster.adventure.api;

import com.dndmaster.adventure.application.scenario.BundleDocumentDraft;
import com.dndmaster.adventure.application.scenario.ScenarioBundleApplicationService;
import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import com.dndmaster.adventure.domain.scenario.OwnerPlayerId;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleDocumentRole;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleId;
import com.dndmaster.adventure.domain.scenario.ScenarioSourceBundle;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping
public class ScenarioBundleController {
    private final ScenarioBundleApplicationService service;

    public ScenarioBundleController(ScenarioBundleApplicationService service) {
        this.service = service;
    }

    @PostMapping("/api/v1/adventures/scenario-bundles")
    ScenarioBundleResponse createBundle(
            @RequestHeader("Authorization") String authorization,
            @RequestBody ScenarioBundleRequest request) {
        OwnerPlayerId owner = ownerFromAuthorization(authorization);
        authorize(owner, request.playerId());
        return ScenarioBundleResponse.from(service.createBundle(owner, request.documents().stream().map(draft ->
                new BundleDocumentDraft(new KnowledgeDocumentId(draft.knowledgeDocumentId()), draft.role())).toList()));
    }

    @PostMapping("/api/v1/adventures/scenario-bundles/{bundleId}/revisions")
    ScenarioBundleResponse reviseBundle(
            @PathVariable UUID bundleId,
            @RequestHeader("Authorization") String authorization,
            @RequestBody ScenarioBundleRequest request) {
        OwnerPlayerId owner = ownerFromAuthorization(authorization);
        authorize(owner, request.playerId());
        return ScenarioBundleResponse.from(service.reviseBundle(
                new ScenarioBundleId(bundleId),
                owner,
                request.documents().stream()
                        .map(draft -> new BundleDocumentDraft(new KnowledgeDocumentId(draft.knowledgeDocumentId()), draft.role()))
                        .toList()));
    }

    @GetMapping("/api/v1/adventures/scenario-bundles/{bundleId}")
    ScenarioBundleResponse readBundle(
            @PathVariable UUID bundleId,
            @RequestHeader("Authorization") String authorization) {
        OwnerPlayerId owner = ownerFromAuthorization(authorization);
        return ScenarioBundleResponse.from(service.readBundle(new ScenarioBundleId(bundleId), owner));
    }

    private static OwnerPlayerId ownerFromAuthorization(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Bearer authorization is required");
        }
        return new OwnerPlayerId(UUID.fromString(authorization.substring("Bearer ".length())));
    }

    private static void authorize(OwnerPlayerId owner, UUID requestPlayerId) {
        if (!owner.value().equals(requestPlayerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "playerId must match Authorization");
        }
    }

    public record ScenarioBundleRequest(UUID playerId, List<ScenarioBundleDocumentRequest> documents) {}

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
            long currentRevision,
            List<ScenarioBundleDocumentResponse> documents) {
        static ScenarioBundleResponse from(ScenarioSourceBundle bundle) {
            return new ScenarioBundleResponse(
                    bundle.id().value(),
                    bundle.ownerPlayerId().value(),
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
