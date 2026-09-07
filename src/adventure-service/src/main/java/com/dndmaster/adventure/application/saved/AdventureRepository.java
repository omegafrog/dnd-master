package com.dndmaster.adventure.application.saved;

import com.dndmaster.adventure.domain.adventure.Adventure;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import com.dndmaster.adventure.domain.adventure.SessionId;
import java.util.List;
import java.util.Optional;

public interface AdventureRepository {
    Optional<Adventure> findById(AdventureId adventureId);
    /** Finds the runtime adventure already associated with a session, if one exists. */
    default Optional<Adventure> findBySessionId(SessionId sessionId) { return Optional.empty(); }
    List<Adventure> findSavedByOwner(OwnerPlayerId ownerPlayerId);
    void save(Adventure adventure);
}
