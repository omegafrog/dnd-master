package com.dndmaster.adventure.application.runtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class LoggingExemplarRetrievalAuditPort implements ExemplarRetrievalAuditPort {
    private static final Logger LOG = LoggerFactory.getLogger(LoggingExemplarRetrievalAuditPort.class);
    @Override public void append(ExemplarRetrievalAudit audit) { LOG.info("exemplar retrieval audit: {}", audit); }
}
