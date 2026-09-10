package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.runtime.RuntimeAddedFact;
import java.util.List;
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

    public RuntimeFactLookupService(ScenarioModelLookupAgentPort scenarioModelLookup) {
        this(scenarioModelLookup, ignored -> StorybookRagResult.notFound());
    }

    public RuntimeFactLookupResult lookup(RuntimeFactLookupRequest request) {
        Objects.requireNonNull(request, "lookup request must not be null");
        String query = lookupTopic(request.query());

        Optional<RuntimeFactLookupResult> established = findEstablished(request, query);
        if (established.isPresent()) return established.get();

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

    /**
     * Resolves against a pre-fetched, session-scoped Storybook pack. This is
     * used by the synchronous runtime turn so source lookup remains separate
     * from dialogue generation while preserving the same precedence order.
     */
    public RuntimeFactLookupResult lookup(RuntimeFactLookupRequest request, List<RuntimeEvidence> storybookEvidence) {
        Objects.requireNonNull(request, "lookup request must not be null");
        storybookEvidence = List.copyOf(Objects.requireNonNull(storybookEvidence, "storybook evidence must not be null"));
        String query = lookupTopic(request.query());
        Optional<RuntimeFactLookupResult> established = findEstablished(request, query);
        if (established.isPresent()) return established.get();

        ScenarioLookupResult modelResult = Objects.requireNonNull(
                scenarioModelLookup.lookup(new ScenarioModelLookupRequest(request.query(), request.lockedScenarioModel())),
                "scenario lookup result must not be null");
        if (modelResult.status() == ScenarioLookupResult.Status.FOUND) {
            validateSupportingElementIds(modelResult, request);
            return RuntimeFactLookupResult.foundScenario(modelResult.answer(), modelResult.supportingElementIds());
        }
        List<RuntimeEvidence> matches = storybookEvidence.stream()
                .filter(evidence -> evidence != null && (contains(evidence.excerpt(), query)
                        || contains(evidence.locator(), query) || (evidence.citationKey() != null && contains(evidence.citationKey(), query))))
                .toList();
        if (!matches.isEmpty()) {
            return RuntimeFactLookupResult.foundRag(matches.getFirst().excerpt(), matches);
        }
        return RuntimeFactLookupResult.notFound();
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
        String normalized = value.toLowerCase(Locale.ROOT);
        return switch (query) {
            case "reward" -> normalized.matches(".*(reward|보상|대가|gold|gp|골드|금화).*");
            case "price" -> normalized.matches(".*(price|값|금액|가격).*");
            case "name" -> normalized.matches(".*(name|이름).*");
            default -> normalized.contains(query);
        };
    }

    private static String lookupTopic(String query) {
        String normalized = query == null ? "" : query.toLowerCase(Locale.ROOT);
        if (normalized.matches(".*(reward|보상|대가|gold|gp|골드|금화).*")) return "reward";
        if (normalized.matches(".*(price|값|금액|가격).*")) return "price";
        if (normalized.matches(".*(name|이름).*")) return "name";
        return normalized;
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
