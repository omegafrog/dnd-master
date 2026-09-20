package com.dndmaster.adventure.application.session;

import com.dndmaster.adventure.domain.adventure.AdventureSession;
import com.dndmaster.adventure.domain.adventure.SessionId;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import java.util.UUID;
import java.util.Optional;
import java.util.List;

public interface AdventureSessionRepository {
    Optional<AdventureSession> findById(SessionId id);
    default List<AdventureSession> findByScenarioPackageId(java.util.UUID scenarioPackageId) { return List.of(); }
    boolean tryAcquireAiRequest(SessionId sessionId, OwnerPlayerId ownerPlayerId, UUID requestId);
    boolean releaseAiRequest(SessionId sessionId, OwnerPlayerId ownerPlayerId, UUID requestId);
    void save(AdventureSession session, long expectedVersion);
}
