package com.dndmaster.adventure.application.saved;

import com.dndmaster.adventure.domain.adventure.Adventure;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import java.util.List;
import java.util.Optional;

public interface AdventureRepository {
    Optional<Adventure> findById(AdventureId adventureId);
    List<Adventure> findSavedByOwner(OwnerPlayerId ownerPlayerId);
    void save(Adventure adventure);
}
