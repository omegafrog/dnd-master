package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.runtime.RuntimeAddedFact;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Resolves established facts first, then searches the locked adventure context and retrieved evidence. */
public final class RuntimeFactLookupService {
    private final ScenarioModelLookupAgentPort scenarioModelLookup;

    public RuntimeFactLookupService(ScenarioModelLookupAgentPort scenarioModelLookup) {
        this.scenarioModelLookup = Objects.requireNonNull(scenarioModelLookup, "scenario model lookup must not be null");
    }

    public RuntimeFactLookupResult lookup(RuntimeFactLookupRequest request) {
        return lookup(request, request.evidence(), null);
    }

    public RuntimeFactLookupResult lookup(UUID soloPlayerId, RuntimeFactLookupRequest request, List<RuntimeEvidence> evidence) {
        return lookup(request, evidence, Objects.requireNonNull(soloPlayerId, "solo player id must not be null"));
    }

    private RuntimeFactLookupResult lookup(RuntimeFactLookupRequest request, List<RuntimeEvidence> evidence, UUID soloPlayerId) {
        Objects.requireNonNull(request, "lookup request must not be null");
        List<RuntimeEvidence> contextualEvidence = List.copyOf(Objects.requireNonNull(evidence,
                "runtime evidence must not be null"));
        if (contextualEvidence.isEmpty()) contextualEvidence = request.evidence();
        String query = request.query();

        Optional<RuntimeFactLookupResult> established = findEstablished(request, query);
        if (established.isPresent()) return established.get();

        ScenarioModelLookupRequest lookupRequest = new ScenarioModelLookupRequest(request.query(),
                request.lockedScenarioModel(), request.currentSituation(), request.currentScene(),
                request.gameState().values(), request.runtimeAddedFacts(), contextualEvidence,
                request.preparedSituation(), request.longTermFacts(), request.knownEvidenceIds());
        ScenarioLookupResult modelResult = Objects.requireNonNull(
                soloPlayerId == null
                        ? scenarioModelLookup.lookup(lookupRequest)
                        : scenarioModelLookup.lookup(soloPlayerId, lookupRequest),
                "scenario lookup result must not be null");
        if (modelResult.status() == ScenarioLookupResult.Status.FOUND) {
            validateSupportingElementIds(modelResult, request);
            return RuntimeFactLookupResult.foundScenario(modelResult.answer(), modelResult.supportingElementIds());
        }

        return RuntimeFactLookupResult.notFound();
    }

    /**
     * Resolves against pre-fetched, session-scoped source excerpts. This is
     * used by the synchronous runtime turn so source lookup remains separate
     * from dialogue generation while preserving the same precedence order.
     */
    public RuntimeFactLookupResult lookup(RuntimeFactLookupRequest request, List<RuntimeEvidence> evidence) {
        return lookup(request, evidence, null);
    }

    private static Optional<RuntimeFactLookupResult> findEstablished(RuntimeFactLookupRequest request, String query) {
        Optional<RuntimeFactLookupResult> gameState = findGameState(request, query);
        if (gameState.isPresent()) return gameState;
        return request.runtimeAddedFacts().stream()
                .filter(fact -> contains(fact.subject(), query) || contains(fact.content(), query))
                .map(fact -> RuntimeFactLookupResult.found(RuntimeFactLookupResult.Source.RUNTIME_ADDED_FACT, fact.content()))
                .findFirst();
    }

    private static Optional<RuntimeFactLookupResult> findGameState(RuntimeFactLookupRequest request, String query) {
        return request.gameState().values().entrySet().stream()
                .filter(entry -> contains(entry.getKey(), query) || contains(String.valueOf(entry.getValue()), query))
                .map(entry -> RuntimeFactLookupResult.found(RuntimeFactLookupResult.Source.GAME_STATE,
                        String.valueOf(entry.getValue())))
                .findFirst();
    }

    private static boolean contains(String value, String query) {
        if (value == null || query == null || query.isBlank()) return false;
        return value.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT));
    }

    private static void validateSupportingElementIds(ScenarioLookupResult result, RuntimeFactLookupRequest request) {
        for (String id : result.supportingElementIds()) {
            if (!request.lockedScenarioModel().containsElement(id)) {
                throw new IllegalArgumentException("supporting element is outside the locked ScenarioModel: " + id);
            }
        }
    }

}
