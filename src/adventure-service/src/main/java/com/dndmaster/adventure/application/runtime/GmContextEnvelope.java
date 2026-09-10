package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.adventure.ActiveSourceContext;
import com.dndmaster.adventure.domain.adventure.AdventureContext;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import com.dndmaster.adventure.domain.runtime.RequestedGmProviderSelection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import com.dndmaster.adventure.domain.runtime.narrative.NarrativeContext;

/** Complete provider input. It carries only the locked session knowledge set. */
public record GmContextEnvelope(
        AdventureId adventureId,
        OwnerPlayerId ownerPlayerId,
        UUID sessionId,
        UUID turnId,
        UUID scenarioPackageId,
        long bindingVersion,
        AdventureContext currentContext,
        ActiveSourceContext activeSourceContext,
        String action,
        EvidencePack evidencePack,
        List<String> recentTurns,
        List<String> characterSnapshots,
        String scenarioContext,
        String provider,
        String model,
        String reasoning,
        RequestedGmProviderSelection requestedSelection,
        NarrativeContext narrativeContext,
        List<String> runtimeFacts,
        List<RuntimeFactLookupResult> factLookupResults) {
    public GmContextEnvelope {
        adventureId = Objects.requireNonNull(adventureId);
        ownerPlayerId = Objects.requireNonNull(ownerPlayerId);
        sessionId = Objects.requireNonNull(sessionId);
        turnId = Objects.requireNonNull(turnId);
        scenarioPackageId = Objects.requireNonNull(scenarioPackageId);
        currentContext = Objects.requireNonNull(currentContext);
        action = required(action);
        evidencePack = Objects.requireNonNull(evidencePack);
        recentTurns = List.copyOf(Objects.requireNonNull(recentTurns));
        characterSnapshots = List.copyOf(Objects.requireNonNull(characterSnapshots));
        runtimeFacts = List.copyOf(Objects.requireNonNull(runtimeFacts, "runtime facts must not be null"));
        if (runtimeFacts.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("runtime facts must not contain blank values");
        }
        factLookupResults = List.copyOf(Objects.requireNonNull(factLookupResults,
                "fact lookup results must not be null"));
        if (factLookupResults.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("fact lookup results must not contain null");
        }
        scenarioContext = scenarioContext == null ? "" : scenarioContext.trim();
        provider = provider == null ? "" : provider.trim();
        model = model == null ? "" : model.trim();
        reasoning = reasoning == null ? "" : reasoning.trim();
        requestedSelection = requestedSelection == null
                ? provider.isBlank() || model.isBlank() || reasoning.isBlank()
                    ? RequestedGmProviderSelection.legacyUnknown()
                    : new RequestedGmProviderSelection(null, provider, model, reasoning)
                : requestedSelection;
        if (bindingVersion < 0) throw new IllegalArgumentException("binding version must not be negative");
    }

    public GmContextEnvelope(com.dndmaster.adventure.domain.adventure.AdventureId adventureId,
                             com.dndmaster.adventure.domain.adventure.OwnerPlayerId ownerPlayerId, UUID scenarioPackageId,
                             long bindingVersion, AdventureContext currentContext, ActiveSourceContext activeSourceContext,
                             String action, EvidencePack evidencePack, List<String> recentTurns) {
        this(adventureId, ownerPlayerId, UUID.randomUUID(), UUID.randomUUID(), scenarioPackageId, bindingVersion, currentContext, activeSourceContext, action,
                evidencePack, recentTurns, List.of(), "", "", "", "", RequestedGmProviderSelection.legacyUnknown(), null, List.of(), List.of());
    }

    public GmContextEnvelope(AdventureId adventureId, OwnerPlayerId ownerPlayerId, UUID sessionId, UUID turnId,
                             UUID scenarioPackageId, long bindingVersion, AdventureContext currentContext,
                             ActiveSourceContext activeSourceContext, String action, EvidencePack evidencePack,
                             List<String> recentTurns, List<String> characterSnapshots, String scenarioContext,
                             String provider, String model, String reasoning) {
        this(adventureId, ownerPlayerId, sessionId, turnId, scenarioPackageId, bindingVersion, currentContext,
                activeSourceContext, action, evidencePack, recentTurns, characterSnapshots, scenarioContext,
                provider, model, reasoning, provider.isBlank() || model.isBlank() || reasoning.isBlank()
                        ? RequestedGmProviderSelection.legacyUnknown()
                : new RequestedGmProviderSelection(null, provider, model, reasoning), null, List.of(), List.of());
    }

    public GmContextEnvelope(AdventureId adventureId, OwnerPlayerId ownerPlayerId, UUID sessionId, UUID turnId,
                             UUID scenarioPackageId, long bindingVersion, AdventureContext currentContext,
                             ActiveSourceContext activeSourceContext, String action, EvidencePack evidencePack,
                             List<String> recentTurns, List<String> characterSnapshots, String scenarioContext,
                             String provider, String model, String reasoning,
                             RequestedGmProviderSelection requestedSelection, NarrativeContext narrativeContext) {
        this(adventureId, ownerPlayerId, sessionId, turnId, scenarioPackageId, bindingVersion, currentContext,
                activeSourceContext, action, evidencePack, recentTurns, characterSnapshots, scenarioContext,
                provider, model, reasoning, requestedSelection, narrativeContext, List.of(), List.of());
    }

    public GmContextEnvelope(AdventureId adventureId, OwnerPlayerId ownerPlayerId, UUID sessionId, UUID turnId,
                             UUID scenarioPackageId, long bindingVersion, AdventureContext currentContext,
                             ActiveSourceContext activeSourceContext, String action, EvidencePack evidencePack,
                             List<String> recentTurns, List<String> characterSnapshots, String scenarioContext,
                             String provider, String model, String reasoning,
                             RequestedGmProviderSelection requestedSelection, NarrativeContext narrativeContext,
                             List<String> runtimeFacts) {
        this(adventureId, ownerPlayerId, sessionId, turnId, scenarioPackageId, bindingVersion, currentContext,
                activeSourceContext, action, evidencePack, recentTurns, characterSnapshots, scenarioContext,
                provider, model, reasoning, requestedSelection, narrativeContext, runtimeFacts, List.of());
    }

    public String operationKey() {
        return adventureId.value() + ":" + scenarioPackageId + ":" + bindingVersion + ":" + turnId;
    }

    private static String required(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("action must not be blank");
        return value.trim();
    }
}
