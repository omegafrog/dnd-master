package com.dndmaster.adventure.api;

import com.dndmaster.adventure.application.runtime.RuntimeBindingApplicationService;
import com.dndmaster.adventure.domain.adventure.*;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/adventures/{adventureId}/runtime-bindings")
public class RuntimeBindingController {
    private final RuntimeBindingApplicationService service;
    private final AuthenticatedPlayerResolver playerResolver;

    public RuntimeBindingController(RuntimeBindingApplicationService service, AuthenticatedPlayerResolver playerResolver) {
        this.service = service;
        this.playerResolver = playerResolver;
    }

    @GetMapping
    RuntimeBindingResponse read(
            @PathVariable UUID adventureId) {
        return RuntimeBindingResponse.from(service.read(
                new AdventureId(adventureId),
                new OwnerPlayerId(playerResolver.playerId())));
    }

    @PostMapping
    RuntimeBindingResponse bind(
            @PathVariable UUID adventureId,
            @RequestBody BindRequest request) {
        OwnerPlayerId owner = new OwnerPlayerId(playerResolver.playerId());
        if (!owner.value().equals(request.playerId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "playerId must match Authorization");
        }
        return RuntimeBindingResponse.from(service.bind(new RuntimeBindingApplicationService.BindRuntimeBindingCommand(
                new AdventureId(adventureId),
                owner,
                request.scenarioPackageId(),
                request.rulebookIds(),
                request.engineId(),
                request.toolIds())));
    }

    @PostMapping("/{bindingVersion}/package-switch")
    RuntimeBindingResponse switchPackage(
            @PathVariable UUID adventureId,
            @PathVariable long bindingVersion,
            @RequestBody SwitchRequest request) {
        OwnerPlayerId owner = new OwnerPlayerId(playerResolver.playerId());
        if (!owner.value().equals(request.playerId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "playerId must match Authorization");
        }
        return RuntimeBindingResponse.from(service.switchScenarioPackage(new RuntimeBindingApplicationService.SwitchRuntimePackageCommand(
                new AdventureId(adventureId), owner, bindingVersion, request.scenarioPackageId())));
    }

    @PostMapping("/{bindingVersion}/source-context")
    RuntimeBindingResponse selectSourceContext(
            @PathVariable UUID adventureId,
            @PathVariable long bindingVersion,
            @RequestBody SelectSourceContextRequest request) {
        OwnerPlayerId owner = new OwnerPlayerId(playerResolver.playerId());
        if (!owner.value().equals(request.playerId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "playerId must match Authorization");
        }
        return RuntimeBindingResponse.from(service.chooseActiveSourceContext(
                new RuntimeBindingApplicationService.ChooseActiveSourceContextCommand(
                        new AdventureId(adventureId), owner, bindingVersion, request.locator())));
    }

    public record BindRequest(
            UUID playerId,
            UUID scenarioPackageId,
            List<UUID> rulebookIds,
            String engineId,
            List<String> toolIds) {}

    public record SwitchRequest(UUID playerId, UUID scenarioPackageId) {}

    public record SelectSourceContextRequest(UUID playerId, String locator) {}

    public record RuntimeBindingResponse(
            UUID adventureId,
            long bindingVersion,
            UUID scenarioPackageId,
            long scenarioPackageRevision,
            List<UUID> rulebookIds,
            long gameSystemDefinitionVersion,
            long characterBlueprintVersion,
            List<PartyMemberResponse> party,
            String engineId,
            List<String> toolIds,
            PlayabilityReportResponse playabilityReport,
            ActiveSourceContextResponse activeSourceContext,
            RuntimeReadinessResponse readiness) {
        static RuntimeBindingResponse from(RuntimeBinding binding) {
            return new RuntimeBindingResponse(
                    binding.adventureId().value(),
                    binding.bindingVersion(),
                    binding.scenarioPackageId(),
                    binding.scenarioPackageRevision(),
                    binding.rulebookIds(),
                    binding.gameSystemDefinitionVersion(),
                    binding.characterBlueprintVersion(),
                    binding.party().stream().map(member -> new PartyMemberResponse(
                            member.characterSheetId().value(), member.controlMode().name(),
                            member.nameMutableAfterStart(), member.raceMutableAfterStart(),
                            member.characterClassMutableAfterStart(), member.backgroundMutableAfterStart(),
                            member.startingAbilitiesMutableAfterStart(), member.levelMutableAfterStart())).toList(),
                    binding.engineId(),
                    binding.toolIds(),
                    PlayabilityReportResponse.from(binding.playabilityReport()),
                    binding.activeSourceContext() == null ? null : ActiveSourceContextResponse.from(binding.activeSourceContext()),
                    RuntimeReadinessResponse.from(binding.readiness()));
        }
    }

    public record RuntimeReadinessResponse(long bindingVersion, String status, List<String> blockers,
                                           List<String> warnings, boolean retryable, boolean ready) {
        static RuntimeReadinessResponse from(RuntimeReadiness readiness) {
            return new RuntimeReadinessResponse(readiness.bindingVersion(), readiness.status().name(), readiness.blockers(),
                    readiness.warnings(), readiness.retryable(), readiness.ready());
        }
    }

    public record PartyMemberResponse(
            UUID characterSheetId,
            String controlMode,
            boolean nameMutableAfterStart,
            boolean raceMutableAfterStart,
            boolean characterClassMutableAfterStart,
            boolean backgroundMutableAfterStart,
            boolean startingAbilitiesMutableAfterStart,
            boolean levelMutableAfterStart) {}

    public record PlayabilityReportResponse(
            String status,
            List<String> warnings,
            List<String> blockers,
            List<String> limits,
            List<SourceContextCandidateResponse> candidates) {
        static PlayabilityReportResponse from(PlayabilityReport report) {
            return new PlayabilityReportResponse(
                    report.status().name(),
                    report.warnings(),
                    report.blockers(),
                    report.limits(),
                    report.candidates().stream().map(SourceContextCandidateResponse::from).toList());
        }
    }

    public record ActiveSourceContextResponse(UUID knowledgeDocumentId, long extractionVersion, String locator, String excerpt) {
        static ActiveSourceContextResponse from(ActiveSourceContext context) {
            return new ActiveSourceContextResponse(
                    context.knowledgeDocumentId().value(), context.extractionVersion(), context.locator(), context.excerpt());
        }
    }

    public record SourceContextCandidateResponse(
            UUID knowledgeDocumentId, long extractionVersion, String locator, String excerpt, double score, String reason) {
        static SourceContextCandidateResponse from(InitialSourceContextCandidate candidate) {
            return new SourceContextCandidateResponse(
                    candidate.knowledgeDocumentId().value(), candidate.extractionVersion(), candidate.locator(),
                    candidate.excerpt(), candidate.score(), candidate.reason());
        }
    }
}
