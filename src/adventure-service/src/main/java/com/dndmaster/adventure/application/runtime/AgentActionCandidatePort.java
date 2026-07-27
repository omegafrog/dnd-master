package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.adventure.AdventureContext;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.CharacterSheetId;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import java.util.Objects;
import java.util.UUID;

public interface AgentActionCandidatePort {
    AgentActionCandidate propose(Request request);

    record Request(
            AdventureId adventureId,
            OwnerPlayerId ownerPlayerId,
            UUID sessionId,
            int turnIndex,
            CharacterSheetId characterSheetId,
            CharacterSheetReadPort.CharacterSheet characterSheet,
            AdventureContext context) {
        public Request {
            Objects.requireNonNull(adventureId);
            Objects.requireNonNull(ownerPlayerId);
            Objects.requireNonNull(sessionId);
            if (turnIndex < 0) throw new IllegalArgumentException("turn index must not be negative");
            Objects.requireNonNull(characterSheetId);
            Objects.requireNonNull(characterSheet);
            Objects.requireNonNull(context);
        }
    }
}
