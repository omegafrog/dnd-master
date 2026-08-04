package com.dndmaster.adventure.api;

import com.dndmaster.adventure.application.scenario.compilation.ResolutionCandidate;
import com.dndmaster.adventure.application.scenario.compilation.ScenarioCompilationApplicationService;
import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import com.dndmaster.adventure.domain.scenario.OwnerPlayerId;
import com.dndmaster.adventure.domain.scenario.ResolutionKind;
import com.dndmaster.adventure.domain.scenario.ResolutionOverride;
import com.dndmaster.adventure.domain.scenario.ResolutionOverrideStatus;
import com.dndmaster.adventure.domain.scenario.ScenarioResolutionDetail;
import com.dndmaster.adventure.domain.scenario.ResolutionVisibility;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleId;
import com.dndmaster.adventure.domain.scenario.ScenarioPackage;
import com.dndmaster.adventure.domain.scenario.ScenarioSourceReference;
import java.util.List;
import java.util.UUID;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final Logger log = LoggerFactory.getLogger(ScenarioCompilationController.class);
    private final ScenarioCompilationApplicationService service;
    private final AuthenticatedPlayerResolver playerResolver;

    public ScenarioCompilationController(
            ScenarioCompilationApplicationService service,
            AuthenticatedPlayerResolver playerResolver) {
        this.service = service;
        this.playerResolver = playerResolver;
    }

    @PostMapping("/scenario-bundles/{bundleId}/compilations")
    PackageResponse compile(
            @PathVariable UUID bundleId,
            @RequestBody CompilationRequest request) {
        OwnerPlayerId owner = new OwnerPlayerId(playerResolver.playerId());
        log.info("scenario compile request bundleId={} owner={}", bundleId, owner.value());
        if (!owner.value().equals(request.playerId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "playerId must match Authorization");
        }
        List<ResolutionCandidate> candidates = request.candidates() == null
                ? List.of()
                : request.candidates().stream().map(ScenarioCompilationController::candidate).toList();
        List<ResolutionOverride> overrides = request.overrides() == null
                ? List.of()
                : request.overrides().stream().map(ScenarioCompilationController::override).toList();
        if (candidates.isEmpty() && overrides.isEmpty()) {
            return PackageResponse.from(service.compile(new ScenarioBundleId(bundleId), owner));
        }
        return PackageResponse.from(service.compile(new ScenarioBundleId(bundleId), owner, candidates, overrides));
    }

    @GetMapping("/scenario-packages/{packageId}")
    PackageResponse read(
            @PathVariable UUID packageId) {
        return PackageResponse.from(service.read(packageId, new OwnerPlayerId(playerResolver.playerId())));
    }

    @GetMapping("/scenario-bundles/{bundleId}/packages")
    List<PackageResponse> listByBundle(@PathVariable UUID bundleId) {
        return service.listByBundleId(new ScenarioBundleId(bundleId), new OwnerPlayerId(playerResolver.playerId()))
                .stream().map(PackageResponse::from).toList();
    }

    @PostMapping("/scenario-bundles/{bundleId}/compilation-jobs")
    CompilationResponse startJob(
            @PathVariable UUID bundleId,
            @RequestBody CompilationJobRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        OwnerPlayerId owner = new OwnerPlayerId(playerResolver.playerId());
        log.info("scenario compilation start request bundleId={} owner={} inputFingerprint={}",
                bundleId, owner.value(), request.inputFingerprint());
        if (!owner.value().equals(request.playerId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "playerId must match Authorization");
        }
        String key = idempotencyKey == null || idempotencyKey.isBlank() ? request.inputFingerprint() : idempotencyKey;
        return CompilationResponse.from(service.start(new ScenarioBundleId(bundleId), owner, request.inputFingerprint(), key));
    }

    @GetMapping("/compilations/{compilationId}")
    CompilationResponse readJob(
            @PathVariable UUID compilationId) {
        OwnerPlayerId owner = new OwnerPlayerId(playerResolver.playerId());
        var compilation = service.readCompilation(compilationId, owner);
        log.info("scenario compilation poll compilationId={} owner={} status={} attempt={} packageId={}",
                compilationId, owner.value(), compilation.status(), compilation.attempt(), compilation.packageId());
        return CompilationResponse.from(compilation);
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

    private static ResolutionOverride override(OverrideRequest request) {
        return ResolutionOverride.create(
                new ScenarioBundleId(request.bundleId()),
                new OwnerPlayerId(request.ownerPlayerId()),
                request.author(),
                request.reason(),
                candidate(request.original()),
                candidate(request.replacement()),
                request.createdAt(),
                request.updatedAt(),
                request.status() == null ? ResolutionOverrideStatus.PENDING : request.status(),
                request.revision());
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

    public record CompilationRequest(UUID playerId, List<CandidateRequest> candidates, List<OverrideRequest> overrides) {}
    public record CompilationJobRequest(UUID playerId, String inputFingerprint) {}
    public record CompilationResponse(UUID compilationId, UUID bundleId, long bundleRevision, String idempotencyKey, String status, int attempt,
                                      UUID packageId, String failureReason) {
            static CompilationResponse from(com.dndmaster.adventure.domain.scenario.ScenarioCompilation compilation) {
                return new CompilationResponse(compilation.id(), compilation.bundleId().value(), compilation.bundleRevision(),
                        compilation.idempotencyKey(), compilation.status().name(), compilation.attempt(), compilation.packageId(), compilation.failureReason());
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
    public record OverrideRequest(
            UUID bundleId,
            UUID ownerPlayerId,
            String author,
            String reason,
            CandidateRequest original,
            CandidateRequest replacement,
            Instant createdAt,
            Instant updatedAt,
            ResolutionOverrideStatus status,
            long revision) {}
    public record PackageResponse(
            UUID packageId, UUID bundleId, long bundleRevision, String inputFingerprint,
            String reportStatus, List<String> warnings, CharacterLimitResponse characterLimit, List<UnitResponse> units) {
        static PackageResponse from(ScenarioPackage scenarioPackage) {
            return new PackageResponse(
                    scenarioPackage.packageId(), scenarioPackage.bundleId().value(), scenarioPackage.bundleRevision(),
                    scenarioPackage.inputFingerprint(), scenarioPackage.report().status().name(),
                    scenarioPackage.report().warnings(), CharacterLimitResponse.from(scenarioPackage.characterLimit()), scenarioPackage.units().stream().map(unit -> new UnitResponse(
                            unit.kind() == null ? null : unit.kind().name(), unit.status().name(), unit.abilityOrSkill(),
                            unit.dc(), unit.diceExpression(), unit.visibility() == null ? null : unit.visibility().name(),
                            unit.sourceQuote(), unit.provenance(), unit.validationMessages(), unit.runtimeCapabilities(),
                            detailResponse(unit.detail()),
                            unit.sourceRefs().stream().map(ScenarioCompilationController::sourceRef).toList())).toList());
        }
    }
    public record CharacterLimitResponse(int maximumCharacters, SourceReferenceRequest source, String sourceQuote) {
        static CharacterLimitResponse from(com.dndmaster.adventure.domain.scenario.CharacterLimit limit) {
            return new CharacterLimitResponse(limit.maximumCharacters(), limit.source().map(ScenarioCompilationController::sourceRef).orElse(null), limit.sourceQuote());
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
