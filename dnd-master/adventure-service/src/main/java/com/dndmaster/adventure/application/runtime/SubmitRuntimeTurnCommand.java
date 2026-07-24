package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import java.util.Objects;

// 플레이어 행동 1회를 런타임 턴으로 처리하라고 넘기는 명령이다.
public record SubmitRuntimeTurnCommand(AdventureId adventureId, OwnerPlayerId ownerPlayerId, String action) {
    public SubmitRuntimeTurnCommand {
        adventureId = Objects.requireNonNull(adventureId, "adventure id must not be null");
        ownerPlayerId = Objects.requireNonNull(ownerPlayerId, "owner player id must not be null");
        if (action == null || action.isBlank()) throw new IllegalArgumentException("action must not be blank");
        action = action.trim();
    }
}
