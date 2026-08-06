package com.dndmaster.ruleknowledge.application.search;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class LoggingEvidencePackObserver implements EvidencePackObserver {
    private static final Logger LOG = LoggerFactory.getLogger(LoggingEvidencePackObserver.class);

    @Override
    public void onAssembled(int candidateCount, int entryCount, boolean degraded, long elapsedNanos) {
        LOG.info("evidence_pack_assembled candidates={} entries={} degraded={} elapsed_ms={}",
                candidateCount, entryCount, degraded, elapsedNanos / 1_000_000d);
    }
}
