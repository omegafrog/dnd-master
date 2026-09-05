package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.runtime.RuntimeAddedFact;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/** Enforces the authoritative lookup order and keeps source access behind typed ports. */
public final class RuntimeFactLookupService {
    private final ScenarioModelLookupAgentPort scenarioModelLookup;
    private final StorybookRagPort storybookRag;

    public RuntimeFactLookupService(ScenarioModelLookupAgentPort scenarioModelLookup, StorybookRagPort storybookRag) {
        this.scenarioModelLookup = Objects.requireNonNull(scenarioModelLookup, "scenario model lookup must not be null");
        this.storybookRag = Objects.requireNonNull(storybookRag, "storybook RAG must not be null");
    }

    public RuntimeFactLookupResult lookup(RuntimeFactLookupRequest request) {
        Objects.requireNonNull(request, "lookup request must not be null");
        String query = request.query().toLowerCase(Locale.ROOT);

        Optional<RuntimeFactLookupResult> gameState = findGameState(request, query);
        if (gameState.isPresent()) return gameState.get();

        Optional<RuntimeFactLookupResult> runtimeFact = request.runtimeAddedFacts().stream()
                .filter(fact -> contains(fact.content(), query))
                .map(fact -> RuntimeFactLookupResult.found(RuntimeFactLookupResult.Source.RUNTIME_ADDED_FACT, fact.content()))
                .findFirst();
        if (runtimeFact.isPresent()) return runtimeFact.get();

        ScenarioLookupResult modelResult = Objects.requireNonNull(
                scenarioModelLookup.lookup(new ScenarioModelLookupRequest(request.query(), request.lockedScenarioModel())),
                "scenario lookup result must not be null");
        if (modelResult.status() == ScenarioLookupResult.Status.FOUND) {
            validateSupportingElementIds(modelResult, request);
            return RuntimeFactLookupResult.foundScenario(modelResult.answer(), modelResult.supportingElementIds());
        }

        StorybookRagResult ragResult = Objects.requireNonNull(
                storybookRag.search(new StorybookRagRequest(request.query())),
                "storybook RAG result must not be null");
        if (ragResult.status() == StorybookRagResult.Status.FOUND) {
            validateStorybookEvidence(ragResult);
            return RuntimeFactLookupResult.foundRag(ragResult.answer(), ragResult.evidence());
        }
        return RuntimeFactLookupResult.notFound();
    }

    private static Optional<RuntimeFactLookupResult> findGameState(RuntimeFactLookupRequest request, String query) {
        return request.gameState().values().entrySet().stream()
                .filter(entry -> contains(entry.getKey(), query) || contains(String.valueOf(entry.getValue()), query))
                .map(entry -> RuntimeFactLookupResult.found(RuntimeFactLookupResult.Source.GAME_STATE,
                        String.valueOf(entry.getValue())))
                .findFirst();
    }

    private static boolean contains(String value, String query) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(query);
    }

    private static void validateSupportingElementIds(ScenarioLookupResult result, RuntimeFactLookupRequest request) {
        for (String id : result.supportingElementIds()) {
            if (!request.lockedScenarioModel().containsElement(id)) {
                throw new IllegalArgumentException("supporting element is outside the locked ScenarioModel: " + id);
            }
        }
    }

    private static void validateStorybookEvidence(StorybookRagResult result) {
        if (result.evidence().stream().anyMatch(evidence -> evidence == null
                || evidence.evidenceType() != RuntimeEvidenceType.STORYBOOK)) {
            throw new IllegalArgumentException("Storybook RAG returned a non-Storybook source reference");
        }
    }
}
