package com.dndmaster.adventure.application.session;

import java.util.List;
import java.util.UUID;

public interface CharacterSheetDeletionPort {
    void delete(UUID sessionId, List<UUID> characterSheetIds);
}
