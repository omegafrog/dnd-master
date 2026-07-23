package com.dndmaster.adventure.api;

import com.dndmaster.adventure.application.scenario.compilation.ResolutionCandidate;
import com.dndmaster.adventure.application.scenario.compilation.ScenarioCompilationApplicationService;
import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import com.dndmaster.adventure.domain.scenario.OwnerPlayerId;
import com.dndmaster.adventure.domain.scenario.ResolutionKind;
import com.dndmaster.adventure.domain.scenario.ResolutionVisibility;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleId;
import com.dndmaster.adventure.domain.scenario.ScenarioPackage;
import com.dndmaster.adventure.domain.scenario.ScenarioSourceReference;
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
@RequestMapping("/api/v1/adventures")
public class ScenarioCompilationController {
    private final ScenarioCompilationApplicationService service;

    public ScenarioCompilationController(ScenarioCompilationApplicationService service) {
        this.service = service;
    }

    @PostMapping("/scenario-bundles/{bundleId}/compilations")
    PackageResponse compile(
            @PathVariable UUID bundleId,
            @RequestHeader("Authorization") String authorization,
            @RequestBody CompilationRequest request) {
        OwnerPlayerId owner = ownerFromAuthorization(authorization);
        if (!owner.value().equals(request.playerId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "playerId must match Authorization");
        }
        List<ResolutionCandidate> candidates = request.candidates().stream().map(ScenarioCompilationController::candidate).toList();
        return PackageResponse.from(service.compile(new ScenarioBundleId(bundleId), owner, candidates));
    }

    @GetMapping("/scenario-packages/{packageId}")
    PackageResponse read(
            @PathVariable UUID packageId,
            @RequestHeader("Authorization") String authorization) {
        return PackageResponse.from(service.read(packageId, ownerFromAuthorization(authorization)));
    }

    private static ResolutionCandidate candidate(CandidateRequest candidate) {
        return new ResolutionCandidate(
                candidate.kind(), candidate.abilityOrSkill(), candidate.dc(), candidate.diceExpression(),
                candidate.visibility(), candidate.sourceQuote(),
                candidate.sourceRefs().stream().map(ref -> new ScenarioSourceReference(
                        new KnowledgeDocumentId(ref.documentId()), ref.extractionVersion(), ref.locator())).toList(),
                candidate.provenance());
    }

    private static OwnerPlayerId ownerFromAuthorization(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Bearer authorization is required");
        }
        try {
            return new OwnerPlayerId(UUID.fromString(authorization.substring("Bearer ".length())));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Bearer authorization is invalid", exception);
        }
    }

    public record CompilationRequest(UUID playerId, List<CandidateRequest> candidates) {}
    public record CandidateRequest(
            ResolutionKind kind, String abilityOrSkill, Integer dc, String diceExpression,
            ResolutionVisibility visibility, String sourceQuote,
            List<SourceReferenceRequest> sourceRefs, String provenance) {}
    public record SourceReferenceRequest(UUID documentId, long extractionVersion, String locator) {}
    public record PackageResponse(
            UUID packageId, UUID bundleId, long bundleRevision, String inputFingerprint,
            String reportStatus, List<String> warnings, List<UnitResponse> units) {
        static PackageResponse from(ScenarioPackage scenarioPackage) {
            return new PackageResponse(
                    scenarioPackage.packageId(), scenarioPackage.bundleId().value(), scenarioPackage.bundleRevision(),
                    scenarioPackage.inputFingerprint(), scenarioPackage.report().status().name(),
                    scenarioPackage.report().warnings(), scenarioPackage.units().stream().map(unit -> new UnitResponse(
                            unit.kind() == null ? null : unit.kind().name(), unit.status().name(),
                            unit.validationMessages(), unit.sourceRefs().stream().map(ScenarioCompilationController::sourceRef).toList())).toList());
        }
    }
    public record UnitResponse(String kind, String status, List<String> validationMessages, List<SourceReferenceRequest> sourceRefs) {}

    private static SourceReferenceRequest sourceRef(ScenarioSourceReference ref) {
        return new SourceReferenceRequest(ref.knowledgeDocumentId().value(), ref.extractionVersion(), ref.locator());
    }
}
