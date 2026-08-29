package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.adventure.ActiveSourceContext;
import com.dndmaster.adventure.domain.adventure.AdventureContext;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.CharacterSheetId;
import com.dndmaster.adventure.domain.adventure.ConversationEntry;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// 저장된 런타임 턴이다. 어떤 근거로 어떤 응답을 냈는지 함께 남긴다.
@JsonIgnoreProperties(ignoreUnknown = true)
public record RuntimeTurn(
        UUID turnId,
        UUID commandId,
        AdventureId adventureId,
        UUID sessionId,
        UUID scenarioPackageId,
        long bindingVersion,
        String action,
        EvidencePack evidencePack,
        RuntimePlan plan,
        ActiveSourceContext activeSourceContext,
        AdventureContext context,
        List<ConversationEntry> conversation,
        long version,
        List<String> citations,
        List<String> warnings,
        boolean committed,
        boolean playerOrigin,
        RuntimeTurnOrigin origin,
        boolean advancesState,
        CharacterSheetId turnCharacterSheetId,
        Integer turnIndex,
        Long expectedVersion,
        boolean gmOnly,
        boolean agentOrigin,
        RuntimeTurnLifecycle lifecycle,
        ResolvedTurnPlan resolvedPlan) {
    public RuntimeTurn {
        turnId = Objects.requireNonNull(turnId, "turn id must not be null");
        commandId = Objects.requireNonNull(commandId, "command id must not be null");
        adventureId = Objects.requireNonNull(adventureId, "adventure id must not be null");
        sessionId = Objects.requireNonNull(sessionId, "session id must not be null");
        scenarioPackageId = Objects.requireNonNull(scenarioPackageId, "scenario package id must not be null");
        action = required(action, "action");
        evidencePack = Objects.requireNonNull(evidencePack, "evidence pack must not be null");
        plan = Objects.requireNonNull(plan, "plan must not be null");
        context = Objects.requireNonNull(context, "context must not be null");
        conversation = List.copyOf(Objects.requireNonNull(conversation, "conversation must not be null"));
        if (version < 0) throw new IllegalArgumentException("version must not be negative");
        citations = List.copyOf(Objects.requireNonNull(citations, "citations must not be null"));
        warnings = List.copyOf(Objects.requireNonNull(warnings, "warnings must not be null"));
        if ((turnCharacterSheetId == null) != (turnIndex == null || turnIndex < 0)) {
            throw new IllegalArgumentException("runtime turn cursor fields must be paired");
        }
        if (gmOnly && agentOrigin) throw new IllegalArgumentException("GM and agent origins are mutually exclusive");
        // Rows written before origin was introduced are deliberately non-player evidence.
        if (origin == null) {
            origin = RuntimeTurnOrigin.GM;
            playerOrigin = false;
        }
        if (playerOrigin != (origin == RuntimeTurnOrigin.PLAYER)) {
            throw new IllegalArgumentException("player origin flag must match durable origin");
        }
        // Rows written before the lifecycle split are already compatibility-presented turns.
        if (lifecycle == null) lifecycle = RuntimeTurnLifecycle.PRESENTED;
        if (resolvedPlan != null && resolvedPlan.lifecycle() == RuntimeTurnLifecycle.PRESENTED
                && lifecycle != RuntimeTurnLifecycle.PRESENTED) {
            throw new IllegalArgumentException("presented artifact requires presented lifecycle");
        }
    }

    public RuntimeTurn(
            UUID turnId,
            UUID commandId,
            AdventureId adventureId,
            UUID sessionId,
            UUID scenarioPackageId,
            long bindingVersion,
            String action,
            EvidencePack evidencePack,
            RuntimePlan plan,
            ActiveSourceContext activeSourceContext,
            AdventureContext context,
            List<ConversationEntry> conversation,
            long version,
            List<String> citations,
            List<String> warnings) {
        this(turnId, commandId, adventureId, sessionId, scenarioPackageId, bindingVersion, action, evidencePack, plan,
                activeSourceContext, context, conversation, version, citations, warnings, false, false, RuntimeTurnOrigin.GM, false,
                null, null, null, false, false, RuntimeTurnLifecycle.PRESENTED, null);
    }

    public RuntimeTurn(UUID turnId, UUID commandId, AdventureId adventureId, UUID sessionId, UUID scenarioPackageId,
            long bindingVersion, String action, EvidencePack evidencePack, RuntimePlan plan, ActiveSourceContext activeSourceContext,
            AdventureContext context, List<ConversationEntry> conversation, long version, List<String> citations, List<String> warnings,
            boolean committed, boolean playerOrigin, RuntimeTurnOrigin origin, boolean advancesState) {
        this(turnId, commandId, adventureId, sessionId, scenarioPackageId, bindingVersion, action, evidencePack, plan, activeSourceContext,
                context, conversation, version, citations, warnings, committed, playerOrigin, origin, advancesState, null, null, null, false, false,
                committed ? RuntimeTurnLifecycle.PRESENTED : RuntimeTurnLifecycle.RESOLVED_UNCOMMITTED, null);
    }

    /** Source-compatible constructor retained for callers compiled before lifecycle fields were added. */
    public RuntimeTurn(UUID turnId, UUID commandId, AdventureId adventureId, UUID sessionId, UUID scenarioPackageId,
            long bindingVersion, String action, EvidencePack evidencePack, RuntimePlan plan, ActiveSourceContext activeSourceContext,
            AdventureContext context, List<ConversationEntry> conversation, long version, List<String> citations, List<String> warnings,
            boolean committed, boolean playerOrigin, RuntimeTurnOrigin origin, boolean advancesState,
            CharacterSheetId turnCharacterSheetId, Integer turnIndex, Long expectedVersion, boolean gmOnly, boolean agentOrigin) {
        this(turnId, commandId, adventureId, sessionId, scenarioPackageId, bindingVersion, action, evidencePack, plan,
                activeSourceContext, context, conversation, version, citations, warnings, committed, playerOrigin, origin,
                advancesState, turnCharacterSheetId, turnIndex, expectedVersion, gmOnly, agentOrigin,
                committed ? RuntimeTurnLifecycle.PRESENTED : RuntimeTurnLifecycle.RESOLVED_UNCOMMITTED, null);
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }

    public RuntimeTurn markCommitted() {
        return committed ? this : new RuntimeTurn(
                turnId, commandId, adventureId, sessionId, scenarioPackageId, bindingVersion, action, evidencePack, plan,
                activeSourceContext, context, conversation, version, citations, warnings, true, playerOrigin, origin, advancesState,
                turnCharacterSheetId, turnIndex, expectedVersion, gmOnly, agentOrigin, RuntimeTurnLifecycle.PRESENTED,
                resolvedPlan == null ? null : resolvedPlan.presented());
    }

    public RuntimeTurnLifecycle lifecycle() {
        return lifecycle;
    }

    public ResolvedTurnPlan resolvedArtifact() {
        return resolvedPlan;
    }

    public RuntimeTurn withResolvedArtifact(ResolvedTurnPlan artifact) {
        Objects.requireNonNull(artifact, "resolved artifact must not be null");
        if (lifecycle != RuntimeTurnLifecycle.RESOLVING && lifecycle != RuntimeTurnLifecycle.RESOLVED_UNCOMMITTED) {
            throw new IllegalStateException("turn is not resolving");
        }
        return new RuntimeTurn(turnId, commandId, adventureId, sessionId, scenarioPackageId, bindingVersion, action, evidencePack,
                plan, activeSourceContext, context, conversation, version, citations, warnings, false, playerOrigin, origin,
                advancesState, turnCharacterSheetId, turnIndex, expectedVersion, gmOnly, agentOrigin,
                RuntimeTurnLifecycle.RESOLVED_UNCOMMITTED, artifact);
    }

    public RuntimeTurn markPresentationFailed() {
        if (resolvedPlan == null || (lifecycle != RuntimeTurnLifecycle.RESOLVED_UNCOMMITTED
                && lifecycle != RuntimeTurnLifecycle.PRESENTATION_FAILED_RETRYABLE)) {
            throw new IllegalStateException("turn has no retryable resolved artifact");
        }
        return new RuntimeTurn(turnId, commandId, adventureId, sessionId, scenarioPackageId, bindingVersion, action,
                evidencePack, plan, activeSourceContext, context, conversation, version, citations, warnings, false,
                playerOrigin, origin, advancesState, turnCharacterSheetId, turnIndex, expectedVersion, gmOnly,
                agentOrigin, RuntimeTurnLifecycle.PRESENTATION_FAILED_RETRYABLE, resolvedPlan);
    }
}
