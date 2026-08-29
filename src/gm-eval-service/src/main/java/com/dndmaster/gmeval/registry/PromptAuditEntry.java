package com.dndmaster.gmeval.registry;

import java.time.Instant;
import java.util.Objects;

/** Append-only operator/reviewer audit event. */
public record PromptAuditEntry(PromptAuditAction action, PromptRole role, PromptVersion promptVersion,
                               String candidateId, String optimizationRunId, String actor,
                               PromptVersion expectedActiveVersion, PromptVersion previousActiveVersion,
                               String reason, Instant occurredAt) {
    public PromptAuditEntry {
        action = Objects.requireNonNull(action, "audit action required");
        role = Objects.requireNonNull(role, "audit role required");
        promptVersion = Objects.requireNonNull(promptVersion, "audit prompt version required");
        if (promptVersion.role() != role) throw new IllegalArgumentException("audit role mismatch");
        if (actor == null || actor.isBlank()) throw new IllegalArgumentException("audit actor required");
        reason = reason == null ? "" : reason;
        occurredAt = occurredAt == null ? Instant.now() : occurredAt;
    }
}
