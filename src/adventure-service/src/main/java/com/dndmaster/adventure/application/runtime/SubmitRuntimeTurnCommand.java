package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import com.dndmaster.adventure.domain.adventure.CharacterSheetId;
import java.util.Objects;
import java.util.UUID;

// 플레이어 행동 1회를 런타임 턴으로 처리하라고 넘기는 명령이다.
public record SubmitRuntimeTurnCommand(
        AdventureId adventureId, OwnerPlayerId ownerPlayerId, UUID turnId, UUID commandId, String action, long expectedVersion,
        CharacterSheetId turnCharacterSheetId, int turnIndex, boolean advancesState) {
    public SubmitRuntimeTurnCommand(AdventureId adventureId, OwnerPlayerId ownerPlayerId, UUID turnId, UUID commandId,
                                    String action, long expectedVersion, CharacterSheetId turnCharacterSheetId, int turnIndex) {
        this(adventureId, ownerPlayerId, turnId, commandId, action, expectedVersion, turnCharacterSheetId, turnIndex, true);
    }
    public SubmitRuntimeTurnCommand(AdventureId adventureId, OwnerPlayerId ownerPlayerId, UUID turnId, UUID commandId, String action) {
        this(adventureId, ownerPlayerId, turnId, commandId, action, -1, null, -1, true);
    }

    public SubmitRuntimeTurnCommand(AdventureId adventureId, OwnerPlayerId ownerPlayerId, UUID turnId, UUID commandId, String action, long expectedVersion) {
        this(adventureId, ownerPlayerId, turnId, commandId, action, expectedVersion, null, -1, true);
    }
    public SubmitRuntimeTurnCommand {
        adventureId = Objects.requireNonNull(adventureId, "adventure id must not be null");
        ownerPlayerId = Objects.requireNonNull(ownerPlayerId, "owner player id must not be null");
        turnId = Objects.requireNonNull(turnId, "turn id must not be null");
        commandId = Objects.requireNonNull(commandId, "command id must not be null");
        if (action == null || action.isBlank()) throw new IllegalArgumentException("action must not be blank");
        action = action.trim();
        if (expectedVersion < -1) throw new IllegalArgumentException("expected version must be -1 or non-negative");
        if (turnIndex < -1) throw new IllegalArgumentException("turn index must be -1 or non-negative");
        if ((turnCharacterSheetId == null) != (turnIndex < 0)) throw new IllegalArgumentException("agent turn cursor fields must be paired");
    }

    public SubmitRuntimeTurnCommand(AdventureId adventureId, OwnerPlayerId ownerPlayerId, UUID turnId, UUID commandId,
                                    String action, long expectedVersion, boolean advancesState) {
        this(adventureId, ownerPlayerId, turnId, commandId, action, expectedVersion, null, -1, advancesState);
    }
}
