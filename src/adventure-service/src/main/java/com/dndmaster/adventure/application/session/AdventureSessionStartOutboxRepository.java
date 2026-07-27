package com.dndmaster.adventure.application.session;

import com.dndmaster.adventure.domain.adventure.SessionId;
import java.util.UUID;

public interface AdventureSessionStartOutboxRepository {
    void prepare(SessionId sessionId, UUID requestId, UUID adventureId, UUID scenarioPackageId);
    void commit(SessionId sessionId, UUID requestId);
}
