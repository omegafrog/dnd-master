package com.dndmaster.adventure.application.session;

import com.dndmaster.adventure.domain.adventure.AiCompanionCandidate;
import com.dndmaster.adventure.domain.adventure.CharacterSheetId;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import com.dndmaster.adventure.domain.adventure.SessionId;

/** Materializes an accepted proposal only; rejected proposals never create a sheet. */
public interface AiCompanionSheetCreationPort {
    CharacterSheetId create(SessionId sessionId, OwnerPlayerId ownerPlayerId, AiCompanionCandidate candidate);
}
