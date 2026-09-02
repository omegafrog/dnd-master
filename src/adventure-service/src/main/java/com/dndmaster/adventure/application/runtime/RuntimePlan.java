package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.adventure.ActiveSourceContext;
import com.dndmaster.adventure.domain.runtime.EffectiveGmProviderSelection;
import com.dndmaster.adventure.domain.runtime.RequestedGmProviderSelection;
import com.dndmaster.adventure.domain.runtime.narrative.StateDelta;
import java.util.List;
import java.util.Objects;

// 계획 단계가 만든 턴 결과 초안이다. 장면, 판정, narration, 다음 근거 위치를 담는다.
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
        boolean advanceStoryPlan,
        String selectedBranchId,
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
        // Rows written before provider metadata was added must remain readable.
        provider = provider == null || provider.isBlank() ? "legacy" : provider.trim();
        model = model == null || model.isBlank() ? "legacy" : model.trim();
        reasoning = reasoning == null ? "" : reasoning.trim();
        selectedBranchId = selectedBranchId == null ? "" : selectedBranchId.trim();
        requestedSelection = requestedSelection == null ? RequestedGmProviderSelection.legacyUnknown() : requestedSelection;
        effectiveSelection = effectiveSelection == null ? EffectiveGmProviderSelection.legacyUnknown() : effectiveSelection;
        if (attemptCount < 1 || attemptCount > 2) throw new IllegalArgumentException("GM candidate attempts must be one or two");
        citationBindings = List.copyOf(Objects.requireNonNull(citationBindings, "citation bindings must not be null"));
    }

    /** Compatibility projection for older API response contracts. */
    public String resolutionStatus() {
        return judgment != null && judgment.startsWith("판정 보류") ? "PENDING_RULE_INPUT" : "RESOLVED";
    }

    public RuntimePlan(String scene, String npcState, String judgment, String narration,
                       ActiveSourceContext proposedActiveSourceContext, List<RuntimeEvidence> citedEvidence,
                       List<String> warnings) {
        this(scene, npcState, judgment, narration, proposedActiveSourceContext, citedEvidence, warnings,
                "legacy", "legacy", "", false, "", RequestedGmProviderSelection.legacyUnknown(), EffectiveGmProviderSelection.legacyUnknown(), 1, List.of(), null);
    }

    public RuntimePlan(String scene, String npcState, String judgment, String narration,
                       ActiveSourceContext proposedActiveSourceContext, List<RuntimeEvidence> citedEvidence,
                       List<String> warnings, String provider, String model, String reasoning) {
        this(scene, npcState, judgment, narration, proposedActiveSourceContext, citedEvidence, warnings,
                provider, model, reasoning, false, "", RequestedGmProviderSelection.legacyUnknown(), EffectiveGmProviderSelection.legacyUnknown(), 1, List.of(), null);
    }

    public RuntimePlan(String scene, String npcState, String judgment, String narration,
                       ActiveSourceContext proposedActiveSourceContext, List<RuntimeEvidence> citedEvidence,
                       List<String> warnings, String provider, String model, String reasoning,
                       boolean advanceStoryPlan, String selectedBranchId) {
        this(scene, npcState, judgment, narration, proposedActiveSourceContext, citedEvidence, warnings,
                provider, model, reasoning, advanceStoryPlan, selectedBranchId,
                RequestedGmProviderSelection.legacyUnknown(), EffectiveGmProviderSelection.legacyUnknown(), 1, List.of(), null);
    }

    public RuntimePlan(String scene, String npcState, String judgment, String narration,
                       ActiveSourceContext proposedActiveSourceContext, List<RuntimeEvidence> citedEvidence,
                       List<String> warnings, String provider, String model, String reasoning,
                       boolean advanceStoryPlan, String selectedBranchId, List<GmCitationBinding> citationBindings) {
        this(scene, npcState, judgment, narration, proposedActiveSourceContext, citedEvidence, warnings,
                provider, model, reasoning, advanceStoryPlan, selectedBranchId,
                RequestedGmProviderSelection.legacyUnknown(), EffectiveGmProviderSelection.legacyUnknown(), 1,
                citationBindings, null);
    }

    public RuntimePlan(String scene, String npcState, String judgment, String narration,
                       ActiveSourceContext proposedActiveSourceContext, List<RuntimeEvidence> citedEvidence,
                       List<String> warnings, String provider, String model, String reasoning,
                       boolean advanceStoryPlan, String selectedBranchId,
                       RequestedGmProviderSelection requestedSelection,
                       EffectiveGmProviderSelection effectiveSelection, int attemptCount) {
        this(scene, npcState, judgment, narration, proposedActiveSourceContext, citedEvidence, warnings,
                provider, model, reasoning, advanceStoryPlan, selectedBranchId,
                requestedSelection, effectiveSelection, attemptCount, List.of(), null);
    }

    public RuntimePlan withStateDelta(StateDelta delta) {
        return new RuntimePlan(scene, npcState, judgment, narration, proposedActiveSourceContext, citedEvidence,
                warnings, provider, model, reasoning, advanceStoryPlan, selectedBranchId, requestedSelection,
                effectiveSelection, attemptCount, citationBindings, delta);
    }

    public RuntimePlan withCitedEvidence(List<RuntimeEvidence> evidence) {
        return new RuntimePlan(scene, npcState, judgment, narration, proposedActiveSourceContext, evidence,
                warnings, provider, model, reasoning, advanceStoryPlan, selectedBranchId, requestedSelection,
                effectiveSelection, attemptCount, citationBindings, stateDelta);
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }
}
