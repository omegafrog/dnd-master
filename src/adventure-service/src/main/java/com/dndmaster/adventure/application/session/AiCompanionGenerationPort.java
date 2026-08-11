package com.dndmaster.adventure.application.session;

import com.dndmaster.adventure.domain.adventure.AiCompanionCandidate;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import com.dndmaster.adventure.domain.adventure.SessionId;

/** AI creates a reviewable candidate only; Party Assembly owns adoption. */
public interface AiCompanionGenerationPort {
    AiCompanionCandidate generate(SessionId sessionId, OwnerPlayerId ownerPlayerId);
}
