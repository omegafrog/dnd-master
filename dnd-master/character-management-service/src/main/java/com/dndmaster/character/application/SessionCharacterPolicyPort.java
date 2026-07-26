package com.dndmaster.character.application;

import com.dndmaster.character.domain.AdventureId;

/** Adventure Runtime remains authority for session ownership and draft policy. */
public interface SessionCharacterPolicyPort {
    SessionCharacterPolicy policyFor(AdventureId sessionId);
}
