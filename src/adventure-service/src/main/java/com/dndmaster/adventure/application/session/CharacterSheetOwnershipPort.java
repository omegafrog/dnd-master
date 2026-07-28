package com.dndmaster.adventure.application.session;

import com.dndmaster.adventure.domain.adventure.CharacterSheetId;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import com.dndmaster.adventure.domain.adventure.SessionId;

public interface CharacterSheetOwnershipPort {
    void verify(SessionId sessionId, OwnerPlayerId ownerPlayerId, CharacterSheetId characterSheetId);
}
