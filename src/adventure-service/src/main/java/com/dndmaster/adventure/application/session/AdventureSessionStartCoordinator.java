package com.dndmaster.adventure.application.session;

import com.dndmaster.adventure.domain.adventure.SessionId;
import java.util.Objects;
import java.util.UUID;
import java.util.List;

/** Durable two-phase coordinator for session start. PREPARED is recoverable; COMMITTED is terminal. */
public final class AdventureSessionStartCoordinator {
    private final AdventureSessionStartOutboxRepository transactionLog;

    public AdventureSessionStartCoordinator(AdventureSessionStartOutboxRepository transactionLog) {
        this.transactionLog = Objects.requireNonNull(transactionLog);
    }

    public void prepare(SessionId sessionId, UUID requestId, UUID adventureId, UUID scenarioPackageId) {
        transactionLog.prepare(sessionId, requestId, adventureId, scenarioPackageId);
    }

    public void commit(SessionId sessionId, UUID requestId) {
        transactionLog.commit(sessionId, requestId);
    }

    public void requestCharacterSheetDeletion(SessionId sessionId, List<UUID> characterSheetIds) {
        transactionLog.requestCharacterSheetDeletion(sessionId, characterSheetIds);
    }
}
