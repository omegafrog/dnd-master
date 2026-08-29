package com.dndmaster.adventure.application.runtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class LoggingNarrativeVerificationAuditPort implements NarrativeVerificationAuditPort {
    private static final Logger LOG = LoggerFactory.getLogger(LoggingNarrativeVerificationAuditPort.class);
    @Override public void append(NarrativeVerificationAudit audit) { LOG.info("narrative verification audit: {}", audit); }
}
