package com.dndmaster.adventure.application.runtime;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

public final class RuntimeTurnFailureClassifier {
    public RuntimeTurnFailureArtifact classify(UUID turnId, RuntimeTurnFailureStage stage,
            Throwable failure, UUID correlationId, int attempt) {
        Throwable root = rootCause(failure);
        String message = root == null || root.getMessage() == null
                ? "" : root.getMessage().toLowerCase(Locale.ROOT);
        RuntimeTurnFailureCode code = root instanceof ToolAuthorizationException
                ? RuntimeTurnFailureCode.TOOL_CAPABILITY_DENIED : code(message, stage);
        boolean retryable = code == RuntimeTurnFailureCode.PROVIDER_TIMEOUT
                || code == RuntimeTurnFailureCode.PROVIDER_UNAVAILABLE;
        String rootCause = root == null ? "unknown" : root.getClass().getName();
        return new RuntimeTurnFailureArtifact(UUID.randomUUID(), turnId, code, stage, retryable,
                rootCause, correlationId == null ? UUID.randomUUID() : correlationId,
                Math.max(1, attempt), Instant.now());
    }

    private static Throwable rootCause(Throwable failure) {
        Throwable root = failure;
        while (root != null && root.getCause() != null) root = root.getCause();
        return root;
    }

    public boolean allowsAutomaticRetry(RuntimeTurnFailureArtifact failure) {
        return failure.retryable() && failure.attempt() <= 2;
    }

    private static RuntimeTurnFailureCode code(String message, RuntimeTurnFailureStage stage) {
        if (message.contains("timeout") || message.contains("timed out")) return RuntimeTurnFailureCode.PROVIDER_TIMEOUT;
        if (message.contains("provider") || message.contains("unavailable") || message.contains("connection")) {
            return RuntimeTurnFailureCode.PROVIDER_UNAVAILABLE;
        }
        if (message.contains("no_meaningful_progress") || message.contains("no meaningful progress")) {
            return RuntimeTurnFailureCode.NO_MEANINGFUL_PROGRESS;
        }
        if (message.contains("safety")) return RuntimeTurnFailureCode.SAFETY_FAILURE;
        if (message.contains("citation")) return RuntimeTurnFailureCode.CITATION_INVALID;
        if (message.contains("json") || message.contains("schema")) return RuntimeTurnFailureCode.JSON_INVALID;
        if (message.contains("judgment") || message.contains("intent")) return RuntimeTurnFailureCode.JUDGMENT_INVALID;
        if (message.contains("narration") || stage == RuntimeTurnFailureStage.PRESENTATION) return RuntimeTurnFailureCode.NARRATION_INVALID;
        if (message.contains("version") || message.contains("conflict")) return RuntimeTurnFailureCode.VERSION_CONFLICT;
        return RuntimeTurnFailureCode.UNKNOWN;
    }
}
