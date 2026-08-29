package com.dndmaster.adventure.application.runtime;

public interface PlanAuditPort {
    void append(PlanSelectionAudit audit);
}
