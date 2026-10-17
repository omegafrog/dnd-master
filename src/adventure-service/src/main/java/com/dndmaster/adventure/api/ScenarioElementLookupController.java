package com.dndmaster.adventure.api;

import com.dndmaster.adventure.application.saved.AdventureRepository;
import com.dndmaster.adventure.application.scenario.compilation.ScenarioPackageRepository;
import com.dndmaster.adventure.domain.adventure.SessionId;
import com.dndmaster.adventure.domain.scenario.ScenarioModel;
import com.dndmaster.adventure.domain.scenario.ScenarioModelElement;
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
    private final ApiRequestGuard requestGuard;

    public ScenarioElementLookupController(AdventureRepository adventures, ScenarioPackageRepository packages,
            @Value("${adventure.integration.internal-token:${INTERNAL_SERVICE_TOKEN:}}") String internalToken) {
        this.adventures = adventures;
        this.packages = packages;
        this.requestGuard = new ApiRequestGuard(internalToken);
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
    private record ScenarioElementMatch(ScenarioModelElement element, int score) { }
}
