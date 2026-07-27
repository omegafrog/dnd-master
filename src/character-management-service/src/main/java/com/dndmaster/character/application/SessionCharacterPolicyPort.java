package com.dndmaster.character.application;

import com.dndmaster.character.domain.AdventureId;
import com.dndmaster.character.domain.CharacterSheetId;

/** Adventure Runtime remains authority for session ownership and draft policy. */
public interface SessionCharacterPolicyPort {
    SessionCharacterPolicy policyFor(AdventureId sessionId);
    default SessionCharacterPolicy policyFor(com.dndmaster.character.domain.SessionId sessionId) {
        return policyFor(sessionId.asAdventureId());
    }
    default SessionCharacterPolicy policyFor(AdventureId sessionId, CharacterSheetId sheetId) { return policyFor(sessionId); }
}
