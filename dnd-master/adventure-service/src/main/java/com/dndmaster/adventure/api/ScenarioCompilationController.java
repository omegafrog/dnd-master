package com.dndmaster.adventure.api;

import com.dndmaster.adventure.application.scenario.compilation.ResolutionCandidate;
import com.dndmaster.adventure.application.scenario.compilation.ScenarioCompilationApplicationService;
import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import com.dndmaster.adventure.domain.scenario.OwnerPlayerId;
import com.dndmaster.adventure.domain.scenario.ResolutionKind;
import com.dndmaster.adventure.domain.scenario.ScenarioResolutionDetail;
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
        if (request.candidates() == null || request.candidates().isEmpty()) {
            return PackageResponse.from(service.compile(new ScenarioBundleId(bundleId), owner));
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

    @PostMapping("/scenario-bundles/{bundleId}/compilation-jobs")
    CompilationResponse startJob(
            @PathVariable UUID bundleId,
            @RequestHeader("Authorization") String authorization,
            @RequestBody CompilationJobRequest request) {
        OwnerPlayerId owner = ownerFromAuthorization(authorization);
        if (!owner.value().equals(request.playerId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "playerId must match Authorization");
        }
        return CompilationResponse.from(service.start(new ScenarioBundleId(bundleId), owner, request.inputFingerprint()));
    }

    @GetMapping("/compilations/{compilationId}")
    CompilationResponse readJob(
            @PathVariable UUID compilationId,
            @RequestHeader("Authorization") String authorization) {
        return CompilationResponse.from(service.readCompilation(compilationId, ownerFromAuthorization(authorization)));
    }

    private static ResolutionCandidate candidate(CandidateRequest candidate) {
        if (candidate == null) {
            return null;
        }
        return new ResolutionCandidate(
                candidate.kind(), candidate.abilityOrSkill(), candidate.dc(), candidate.diceExpression(),
                candidate.visibility(), candidate.sourceQuote(),
                sourceRefs(candidate.sourceRefs()),
                candidate.provenance(),
                detail(candidate.detail()));
    }

    private static ScenarioResolutionDetail detail(DetailRequest detail) {
        if (detail == null) return null;
        return new ScenarioResolutionDetail(
                detail.triggerCondition(),
                detail.actor(),
                detail.roller(),
                detail.instructionVisibility(),
                detail.resultVisibility(),
                detail.modifiers(),
                detail.advantageState(),
                detail.reroll(),
                detail.steps() == null ? List.of() : detail.steps().stream().map(step -> new ScenarioResolutionDetail.Step(
                        step.id(), step.kind(), step.abilityOrSkill(), step.dc(), step.diceExpression(), step.condition(),
                        step.nextStepIds(), step.successOutcomeIds(), step.failureOutcomeIds(), sourceRefs(step.sourceRefs()))).toList(),
                detail.outcomes() == null ? List.of() : detail.outcomes().stream().map(outcome -> new ScenarioResolutionDetail.Outcome(
                        outcome.id(), outcome.label(), outcome.description(), sourceRefs(outcome.sourceRefs()))).toList(),
                detail.randomTable() == null ? List.of() : detail.randomTable().stream().map(entry -> new ScenarioResolutionDetail.TableEntry(
                        entry.range(), entry.outcome(), sourceRefs(entry.sourceRefs()))).toList(),
                detail.tableCoverage());
    }

    private static List<ScenarioSourceReference> sourceRefs(List<SourceReferenceRequest> refs) {
        if (refs == null) return null;
        try {
            return refs.stream().map(ref -> ref == null ? null : new ScenarioSourceReference(
                    new KnowledgeDocumentId(ref.documentId()), ref.extractionVersion(), ref.locator())).toList();
        } catch (RuntimeException malformed) {
            return null;
        }
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
    public record CompilationJobRequest(UUID playerId, String inputFingerprint) {}
    public record CompilationResponse(UUID compilationId, UUID bundleId, long bundleRevision, String status, int attempt,
                                      UUID packageId, String failureReason) {
        static CompilationResponse from(com.dndmaster.adventure.domain.scenario.ScenarioCompilation compilation) {
            return new CompilationResponse(compilation.id(), compilation.bundleId().value(), compilation.bundleRevision(),
                    compilation.status().name(), compilation.attempt(), compilation.packageId(), compilation.failureReason());
        }
    }
    public record CandidateRequest(
            ResolutionKind kind, String abilityOrSkill, Integer dc, String diceExpression,
            ResolutionVisibility visibility, String sourceQuote,
            List<SourceReferenceRequest> sourceRefs, String provenance, DetailRequest detail) {}
    public record DetailRequest(
            String triggerCondition,
            String actor,
            String roller,
            String instructionVisibility,
            String resultVisibility,
            List<String> modifiers,
            String advantageState,
            String reroll,
            List<StepRequest> steps,
            List<OutcomeRequest> outcomes,
            List<TableEntryRequest> randomTable,
            String tableCoverage) {}
    public record StepRequest(
            String id,
            ResolutionKind kind,
            String abilityOrSkill,
            Integer dc,
            String diceExpression,
            String condition,
            List<String> nextStepIds,
            List<String> successOutcomeIds,
            List<String> failureOutcomeIds,
            List<SourceReferenceRequest> sourceRefs) {}
    public record OutcomeRequest(String id, String label, String description, List<SourceReferenceRequest> sourceRefs) {}
    public record TableEntryRequest(String range, String outcome, List<SourceReferenceRequest> sourceRefs) {}
    public record SourceReferenceRequest(UUID documentId, long extractionVersion, String locator) {}
    public record PackageResponse(
            UUID packageId, UUID bundleId, long bundleRevision, String inputFingerprint,
            String reportStatus, List<String> warnings, List<UnitResponse> units) {
        static PackageResponse from(ScenarioPackage scenarioPackage) {
            return new PackageResponse(
                    scenarioPackage.packageId(), scenarioPackage.bundleId().value(), scenarioPackage.bundleRevision(),
                    scenarioPackage.inputFingerprint(), scenarioPackage.report().status().name(),
                    scenarioPackage.report().warnings(), scenarioPackage.units().stream().map(unit -> new UnitResponse(
                            unit.kind() == null ? null : unit.kind().name(), unit.status().name(), unit.abilityOrSkill(),
                            unit.dc(), unit.diceExpression(), unit.visibility() == null ? null : unit.visibility().name(),
                            unit.sourceQuote(), unit.provenance(), unit.validationMessages(), unit.runtimeCapabilities(),
                            detailResponse(unit.detail()),
                            unit.sourceRefs().stream().map(ScenarioCompilationController::sourceRef).toList())).toList());
        }
    }
    private static DetailRequest detailResponse(ScenarioResolutionDetail detail) {
        return new DetailRequest(
                detail.triggerCondition(), detail.actor(), detail.roller(), detail.instructionVisibility(), detail.resultVisibility(),
                detail.modifiers(), detail.advantageState(), detail.reroll(),
                detail.steps().stream().map(step -> new StepRequest(
                        step.id(), step.kind(), step.abilityOrSkill(), step.dc(), step.diceExpression(), step.condition(),
                        step.nextStepIds(), step.successOutcomeIds(), step.failureOutcomeIds(),
                        step.sourceRefs().stream().map(ScenarioCompilationController::sourceRef).toList())).toList(),
                detail.outcomes().stream().map(outcome -> new OutcomeRequest(
                        outcome.id(), outcome.label(), outcome.description(),
                        outcome.sourceRefs().stream().map(ScenarioCompilationController::sourceRef).toList())).toList(),
                detail.randomTable().stream().map(entry -> new TableEntryRequest(
                        entry.range(), entry.outcome(),
                        entry.sourceRefs().stream().map(ScenarioCompilationController::sourceRef).toList())).toList(),
                detail.tableCoverage());
    }
    public record UnitResponse(String kind, String status, String abilityOrSkill, Integer dc, String diceExpression,
                               String visibility, String sourceQuote, String provenance, List<String> validationMessages,
                               List<String> runtimeCapabilities, DetailRequest detail,
                               List<SourceReferenceRequest> sourceRefs) {}

    private static SourceReferenceRequest sourceRef(ScenarioSourceReference ref) {
        return new SourceReferenceRequest(ref.knowledgeDocumentId().value(), ref.extractionVersion(), ref.locator());
    }
}
