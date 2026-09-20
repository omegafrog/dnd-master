package com.dndmaster.adventure.application.combat;

import com.dndmaster.adventure.domain.runtime.PendingMapMovementConfirmation;
import java.util.Optional;
import java.util.UUID;

public interface PendingMapMovementConfirmationRepository {
    Optional<PendingMapMovementConfirmation> findByAdventureId(UUID adventureId, UUID ownerPlayerId);
    void save(PendingMapMovementConfirmation confirmation);
    void deleteByAdventureId(UUID adventureId, UUID ownerPlayerId);
}
