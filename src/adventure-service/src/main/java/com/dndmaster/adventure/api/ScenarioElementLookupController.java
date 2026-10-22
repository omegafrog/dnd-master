package com.dndmaster.adventure.api;

import com.dndmaster.adventure.application.saved.AdventureRepository;
import com.dndmaster.adventure.application.scenario.compilation.ScenarioPackageRepository;
import com.dndmaster.adventure.domain.adventure.SessionId;
import com.dndmaster.adventure.domain.scenario.ScenarioModel;
import com.dndmaster.adventure.domain.scenario.ScenarioModelElement;
import com.dndmaster.adventure.domain.scenario.StageArtifactRepository;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/** Read-only, execution-scoped lookup of exact elements in a locked scenario package. */
@RestController
public final class ScenarioElementLookupController {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(ScenarioElementLookupController.class);
    private final AdventureRepository adventures;
    private final ScenarioPackageRepository packages;
    private final StageArtifactRepository stageArtifacts;
    private final ApiRequestGuard requestGuard;

    public ScenarioElementLookupController(AdventureRepository adventures, ScenarioPackageRepository packages,
            StageArtifactRepository stageArtifacts,
            @Value("${adventure.integration.internal-token:${INTERNAL_SERVICE_TOKEN:}}") String internalToken) {
        this.adventures = adventures;
        this.packages = packages;
        this.stageArtifacts = stageArtifacts;
        this.requestGuard = new ApiRequestGuard(internalToken);
    }

    @PostMapping("/internal/v1/adventures/situations/available")
    AvailableSituationResponse availableSituations(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestBody AvailableSituationRequest request) {
        requestGuard.internal(token);
        long started = com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.begin(LOGGER,
                "mcp_available_situations", "sessionId=" + request.sessionId() + " packageId=" + request.scenarioPackageId());
        try {
            if (request.ownerId() == null || request.sessionId() == null || request.scenarioPackageId() == null) {
                throw new ApiRequestGuard.ApiContractException(400, "INVALID_SITUATION_LOOKUP_SCOPE");
            }
            var adventure = adventures.findBySessionId(new SessionId(request.sessionId()))
                    .filter(value -> value.ownerPlayerId().value().equals(request.ownerId()))
                    .filter(value -> request.scenarioPackageId().equals(value.lockedScenarioPackageId()))
                    .orElseThrow(() -> new ApiRequestGuard.ApiContractException(404, "LOCKED_SCENARIO_NOT_FOUND"));
            var state = adventure.storyRuntimeState();
            if (state == null || state.isOpenPlay()) {
                com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.complete(LOGGER,
                        "mcp_available_situations", "sessionId=" + request.sessionId() + " packageId="
                                + request.scenarioPackageId() + " candidateCount=0 status=NOT_FOUND reason=NO_ACTIVE_STAGE", started);
                return new AvailableSituationResponse("NOT_FOUND", List.of());
            }
            var stage = stageArtifacts.findDetailedStage(adventure.id(), state.scenarioPackageId(), state.stageId(),
                            state.backboneRevision(), state.detailedStageRevision())
                    .orElseThrow(() -> new ApiRequestGuard.ApiContractException(404, "ACTIVE_PREPARED_STAGE_NOT_FOUND"));
            if (stage.situations().stream().anyMatch(item -> item.situationId().equals(state.activeSituationId()) && item.isEnding())) {
                com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.complete(LOGGER,
                        "mcp_available_situations", "sessionId=" + request.sessionId() + " packageId="
                                + request.scenarioPackageId() + " stageId=" + state.stageId()
                                + " candidateCount=0 status=NOT_FOUND reason=ACTIVE_ENDING_SITUATION", started);
                return new AvailableSituationResponse("NOT_FOUND", List.of());
            }
            var backbone = stageArtifacts.findBackbone(adventure.id(), state.scenarioPackageId(), state.backboneRevision())
                    .orElseThrow(() -> new ApiRequestGuard.ApiContractException(404, "PREPARED_STORY_GRAPH_NOT_FOUND"));
            var allStages = backbone.stages().stream().map(entry -> stageArtifacts.findDetailedStage(adventure.id(),
                    state.scenarioPackageId(), entry.stageId(), state.backboneRevision(), 1)
                    .orElseThrow(() -> new ApiRequestGuard.ApiContractException(404, "PREPARED_STORY_STAGE_NOT_FOUND"))).toList();
            var candidates = com.dndmaster.adventure.domain.runtime.story.StoryRuntimeRules
                    .availableSituationCandidates(state, stage, allStages).stream()
                    .map(situation -> new AvailableSituationCandidate(situation.situationId(), situation.intents(),
                            situation.required(), situation.prerequisiteSituationIds(), situation.revelationIds(),
                            situation.pressureIds(), situation.funnelIds(), situation.importantConsequenceIds(),
                            situation.mapNeeded(), allStages.stream().filter(item -> item.situations().contains(situation))
                                    .map(com.dndmaster.adventure.domain.scenario.DetailedStage::stageId).findFirst().orElse(state.stageId()),
                            situation.title(), situation.description(), situation.isEnding()))
                    .toList();
            String status = candidates.isEmpty() ? "NOT_FOUND" : "FOUND";
            com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.complete(LOGGER,
                    "mcp_available_situations", "sessionId=" + request.sessionId() + " packageId="
                            + request.scenarioPackageId() + " stageId=" + state.stageId() + " expectedCandidateCount="
                            + stage.situations().size() + " actualCandidateCount=" + candidates.size() + " status=" + status, started);
            return new AvailableSituationResponse(status, candidates);
        } catch (RuntimeException | Error failure) {
            com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.fail(LOGGER,
                    "mcp_available_situations", "sessionId=" + request.sessionId() + " packageId="
                            + request.scenarioPackageId() + " recoveryAction=return_lookup_failure", started, failure);
            throw failure;
        }
    }

    @PostMapping("/internal/v1/adventures/scenario-elements/search")
    ScenarioElementSearchResponse search(@RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestBody ScenarioElementSearchRequest request) {
        requestGuard.internal(token);
        long started = com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.begin(LOGGER,
                "mcp_scenario_lookup", "sessionId=" + request.sessionId() + " packageId=" + request.scenarioPackageId()
                        + " queryChars=" + (request.query() == null ? 0 : request.query().length()));
        try {
        if (request.ownerId() == null || request.sessionId() == null || request.scenarioPackageId() == null) {
            throw new ApiRequestGuard.ApiContractException(400, "INVALID_SCENARIO_LOOKUP_SCOPE");
        }
        if (request.query() == null || request.query().isBlank() || request.query().length() > 1000) {
            throw new ApiRequestGuard.ApiContractException(400, "INVALID_SCENARIO_QUERY");
        }
        var adventure = adventures.findBySessionId(new SessionId(request.sessionId()))
                .filter(value -> value.ownerPlayerId().value().equals(request.ownerId()))
                .filter(value -> request.scenarioPackageId().equals(value.lockedScenarioPackageId()))
                .orElseThrow(() -> new ApiRequestGuard.ApiContractException(404, "LOCKED_SCENARIO_NOT_FOUND"));
        ScenarioModel model = packages.findById(adventure.lockedScenarioPackageId())
                .map(value -> value.scenarioModel())
                .orElseThrow(() -> new ApiRequestGuard.ApiContractException(404, "LOCKED_SCENARIO_NOT_FOUND"));
        List<String> terms = terms(request.query());
        List<ScenarioElementResult> matches = elements(model).stream()
                .map(element -> new ScenarioElementMatch(element, score(element, terms)))
                .filter(match -> match.score() > 0)
                .sorted(Comparator.comparingInt(ScenarioElementMatch::score).reversed()
                        .thenComparing(match -> match.element().elementId()))
                .limit(20)
                .map(match -> new ScenarioElementResult(match.element().elementId(), match.element().type(),
                        match.element().attributes()))
                .toList();
        com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.complete(LOGGER,
                "mcp_scenario_lookup", "sessionId=" + request.sessionId() + " packageId=" + request.scenarioPackageId()
                        + " queryChars=" + request.query().length() + " elementCount=" + model.elementIds().size()
                        + " matchedCount=" + matches.size() + " status=" + (matches.isEmpty() ? "NOT_FOUND" : "FOUND"), started);
        return new ScenarioElementSearchResponse(matches.isEmpty() ? "NOT_FOUND" : "FOUND", matches);
        } catch (RuntimeException | Error failure) {
            com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.fail(LOGGER,
                    "mcp_scenario_lookup", "sessionId=" + request.sessionId() + " packageId="
                            + request.scenarioPackageId() + " queryChars=" + (request.query() == null ? 0 : request.query().length()),
                    started, failure);
            throw failure;
        }
    }

    private static List<ScenarioModelElement> elements(ScenarioModel model) {
        List<ScenarioModelElement> result = new ArrayList<>();
        result.addAll(model.actors()); result.addAll(model.locations()); result.addAll(model.objectives());
        result.addAll(model.revelations()); result.addAll(model.encounters()); result.addAll(model.relationships());
        result.addAll(model.resolutionCriteria());
        return result;
    }

    private static int score(ScenarioModelElement element, List<String> terms) {
        String searchable = normalize(element.elementId() + " " + element.type() + " " + element.attributes());
        return (int) terms.stream().filter(searchable::contains).count();
    }

    private static List<String> terms(String query) {
        return List.of(normalize(query).split("[^\\p{L}\\p{N}]+" )).stream().filter(term -> term.length() > 1).distinct().toList();
    }

    private static String normalize(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
    }

    public record ScenarioElementSearchRequest(UUID ownerId, UUID sessionId, UUID scenarioPackageId, String query) { }
    public record ScenarioElementSearchResponse(String status, List<ScenarioElementResult> elements) { }
    public record ScenarioElementResult(String elementId, String type, java.util.Map<String, Object> attributes) { }
    public record AvailableSituationRequest(UUID ownerId, UUID sessionId, UUID scenarioPackageId) { }
    public record AvailableSituationResponse(String status, List<AvailableSituationCandidate> situations) { }
    public record AvailableSituationCandidate(String situationId, List<com.dndmaster.adventure.domain.scenario.StageIntent> intents,
            boolean required, List<String> prerequisiteSituationIds, List<String> revelationIds,
            List<String> pressureIds, List<String> funnelIds, List<String> importantConsequenceIds, boolean mapNeeded,
            String stageId, String title, String description, boolean isEnding) { }
    private record ScenarioElementMatch(ScenarioModelElement element, int score) { }
}
