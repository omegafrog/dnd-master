package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.adventure.AdventureContext;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.CharacterSheetId;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import java.util.Objects;

public interface AgentActionCandidatePort {
    AgentActionCandidate propose(Request request);

    record Request(
            AdventureId adventureId,
            OwnerPlayerId ownerPlayerId,
            CharacterSheetId characterSheetId,
            CharacterSheetReadPort.CharacterSheet characterSheet,
            AdventureContext context) {
        public Request {
            Objects.requireNonNull(adventureId);
            Objects.requireNonNull(ownerPlayerId);
            Objects.requireNonNull(characterSheetId);
            Objects.requireNonNull(characterSheet);
            Objects.requireNonNull(context);
        }
    }
}
