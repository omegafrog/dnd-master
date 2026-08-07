package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.adventure.ActiveSourceContext;
import com.dndmaster.adventure.domain.adventure.AdventureContext;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.ConversationEntry;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

// 저장된 런타임 턴이다. 어떤 근거로 어떤 응답을 냈는지 함께 남긴다.
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
        boolean committed) {
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
                activeSourceContext, context, conversation, version, citations, warnings, false);
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }

    public RuntimeTurn markCommitted() {
        return committed ? this : new RuntimeTurn(
                turnId, commandId, adventureId, sessionId, scenarioPackageId, bindingVersion, action, evidencePack, plan,
                activeSourceContext, context, conversation, version, citations, warnings, true);
    }

    public String evidenceCondition() {
        return warnings.stream().filter(warning -> warning.startsWith("rag-condition:"))
                .map(warning -> warning.substring("rag-condition:".length())).findFirst().orElse("CURRENT_RAG");
    }

    public PlayerProjection playerProjection(java.util.Set<String> committedEvents) {
        return PlayerProjection.create(plan.narration(), plan.judgment(), context.currentScene(), citations,
                warnings, List.of(), evidencePack.all(), committedEvents, version);
    }
}
