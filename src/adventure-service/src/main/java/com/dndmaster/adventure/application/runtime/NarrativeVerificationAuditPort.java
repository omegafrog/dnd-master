package com.dndmaster.adventure.application.runtime;

@FunctionalInterface
public interface NarrativeVerificationAuditPort {
    void append(NarrativeVerificationAudit audit);
}
