package com.dndmaster.adventure.application.session;

import com.dndmaster.adventure.domain.adventure.SessionId;
import java.util.UUID;
import java.util.List;
import java.util.Optional;

public interface AdventureSessionStartOutboxRepository {
    void prepare(SessionId sessionId, UUID requestId, UUID adventureId, UUID scenarioPackageId);
    void commit(SessionId sessionId, UUID requestId);
    default void requestCharacterSheetDeletion(SessionId sessionId, List<UUID> characterSheetIds) {}
    default Optional<DeletionEvent> claimNextDeletion() { return Optional.empty(); }
    default void completeDeletion(UUID eventId) {}
    default void failDeletion(UUID eventId, String reason) {}
    record DeletionEvent(UUID eventId, UUID sessionId, List<UUID> characterSheetIds, int attempts) {}
}
