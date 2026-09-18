package com.dndmaster.combatmap.application.movement;

import com.dndmaster.combatmap.domain.PlayerId;
import java.util.Objects;

/** 판정 대기와 제출을 소유한 플레이어의 타입 계약. */
public record MovementCheckOwner(MovementCheckActor actor, PlayerId playerId) {
    public MovementCheckOwner {
        actor = Objects.requireNonNull(actor, "check actor must not be null");
        playerId = Objects.requireNonNull(playerId, "check owner player id must not be null");
        if (actor != MovementCheckActor.PLAYER) throw new IllegalArgumentException("unsupported check actor");
    }

    public static MovementCheckOwner player(PlayerId playerId) {
        return new MovementCheckOwner(MovementCheckActor.PLAYER, playerId);
    }
}
