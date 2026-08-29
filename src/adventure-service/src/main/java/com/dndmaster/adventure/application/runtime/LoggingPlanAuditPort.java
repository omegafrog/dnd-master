package com.dndmaster.adventure.application.runtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class LoggingPlanAuditPort implements PlanAuditPort {
    private static final Logger LOG = LoggerFactory.getLogger(LoggingPlanAuditPort.class);
    @Override public void append(PlanSelectionAudit audit) { LOG.info("plan selection audit: {}", audit); }
}
