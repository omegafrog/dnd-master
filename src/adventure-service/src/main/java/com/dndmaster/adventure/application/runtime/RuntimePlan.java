package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.adventure.ActiveSourceContext;
import com.dndmaster.adventure.domain.runtime.EffectiveGmProviderSelection;
import com.dndmaster.adventure.domain.runtime.RequestedGmProviderSelection;
import com.dndmaster.adventure.domain.runtime.narrative.StateDelta;
import java.util.List;
import java.util.Objects;

/** Typed result produced by the Scenario Model runtime planner. */
public record RuntimePlan(
        String scene,
        String npcState,
        String judgment,
        String narration,
        ActiveSourceContext proposedActiveSourceContext,
        List<RuntimeEvidence> citedEvidence,
        List<String> warnings,
        String provider,
        String model,
        String reasoning,
        boolean stateTransitionRequested,
        String requestedSelectionId,
        RequestedGmProviderSelection requestedSelection,
        EffectiveGmProviderSelection effectiveSelection,
        int attemptCount,
        List<GmCitationBinding> citationBindings,
        StateDelta stateDelta) {
    public RuntimePlan {
        scene = required(scene, "scene");
        judgment = required(judgment, "judgment");
        narration = required(narration, "narration");
        citedEvidence = List.copyOf(Objects.requireNonNull(citedEvidence, "cited evidence must not be null"));
        warnings = List.copyOf(Objects.requireNonNull(warnings, "warnings must not be null"));
        provider = provider == null || provider.isBlank() ? "scenario-runtime" : provider.trim();
        model = model == null || model.isBlank() ? "scenario-runtime" : model.trim();
        reasoning = reasoning == null ? "" : reasoning.trim();
        requestedSelectionId = requestedSelectionId == null ? "" : requestedSelectionId.trim();
        requestedSelection = requestedSelection == null ? RequestedGmProviderSelection.legacyUnknown() : requestedSelection;
        effectiveSelection = effectiveSelection == null ? EffectiveGmProviderSelection.legacyUnknown() : effectiveSelection;
        if (attemptCount < 1 || attemptCount > 2) throw new IllegalArgumentException("GM candidate attempts must be one or two");
        citationBindings = List.copyOf(Objects.requireNonNull(citationBindings, "citation bindings must not be null"));
    }

    public String resolutionStatus() {
        return judgment != null && judgment.startsWith("판정 보류") ? "PENDING_RULE_INPUT" : "RESOLVED";
    }

    public RuntimePlan(String scene, String npcState, String judgment, String narration,
                       ActiveSourceContext activeSourceContext, List<RuntimeEvidence> citedEvidence,
                       List<String> warnings) {
        this(scene, npcState, judgment, narration, activeSourceContext, citedEvidence, warnings,
                "scenario-runtime", "scenario-runtime", "", false, "",
                RequestedGmProviderSelection.legacyUnknown(), EffectiveGmProviderSelection.legacyUnknown(), 1, List.of(), null);
    }

    public RuntimePlan(String scene, String npcState, String judgment, String narration,
                       ActiveSourceContext activeSourceContext, List<RuntimeEvidence> citedEvidence,
                       List<String> warnings, String provider, String model, String reasoning) {
        this(scene, npcState, judgment, narration, activeSourceContext, citedEvidence, warnings,
                provider, model, reasoning, false, "",
                RequestedGmProviderSelection.legacyUnknown(), EffectiveGmProviderSelection.legacyUnknown(), 1, List.of(), null);
    }

    public RuntimePlan(String scene, String npcState, String judgment, String narration,
                       ActiveSourceContext activeSourceContext, List<RuntimeEvidence> citedEvidence,
                       List<String> warnings, String provider, String model, String reasoning,
                       boolean stateTransitionRequested, String requestedSelectionId) {
        this(scene, npcState, judgment, narration, activeSourceContext, citedEvidence, warnings,
                provider, model, reasoning, stateTransitionRequested, requestedSelectionId,
                RequestedGmProviderSelection.legacyUnknown(), EffectiveGmProviderSelection.legacyUnknown(), 1, List.of(), null);
    }

    public RuntimePlan(String scene, String npcState, String judgment, String narration,
                       ActiveSourceContext activeSourceContext, List<RuntimeEvidence> citedEvidence,
                       List<String> warnings, String provider, String model, String reasoning,
                       boolean stateTransitionRequested, String requestedSelectionId, List<GmCitationBinding> citationBindings) {
        this(scene, npcState, judgment, narration, activeSourceContext, citedEvidence, warnings,
                provider, model, reasoning, stateTransitionRequested, requestedSelectionId,
                RequestedGmProviderSelection.legacyUnknown(), EffectiveGmProviderSelection.legacyUnknown(), 1,
                citationBindings, null);
    }

    public RuntimePlan(String scene, String npcState, String judgment, String narration,
                       ActiveSourceContext activeSourceContext, List<RuntimeEvidence> citedEvidence,
                       List<String> warnings, String provider, String model, String reasoning,
                       boolean stateTransitionRequested, String requestedSelectionId,
                       RequestedGmProviderSelection requestedSelection,
                       EffectiveGmProviderSelection effectiveSelection, int attemptCount) {
        this(scene, npcState, judgment, narration, activeSourceContext, citedEvidence, warnings,
                provider, model, reasoning, stateTransitionRequested, requestedSelectionId,
                requestedSelection, effectiveSelection, attemptCount, List.of(), null);
    }

    public RuntimePlan withStateDelta(StateDelta delta) {
        return new RuntimePlan(scene, npcState, judgment, narration, proposedActiveSourceContext, citedEvidence,
                warnings, provider, model, reasoning, stateTransitionRequested, requestedSelectionId, requestedSelection,
                effectiveSelection, attemptCount, citationBindings, delta);
    }

    public RuntimePlan withCitedEvidence(List<RuntimeEvidence> evidence) {
        return new RuntimePlan(scene, npcState, judgment, narration, proposedActiveSourceContext, evidence,
                warnings, provider, model, reasoning, stateTransitionRequested, requestedSelectionId, requestedSelection,
                effectiveSelection, attemptCount, citationBindings, stateDelta);
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }
}
