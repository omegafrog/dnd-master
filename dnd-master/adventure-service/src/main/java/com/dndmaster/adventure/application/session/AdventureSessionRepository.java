package com.dndmaster.adventure.application.session;

import com.dndmaster.adventure.domain.adventure.AdventureSession;
import com.dndmaster.adventure.domain.adventure.SessionId;
import java.util.Optional;

public interface AdventureSessionRepository {
    Optional<AdventureSession> findById(SessionId id);
    void save(AdventureSession session, long expectedVersion);
}
