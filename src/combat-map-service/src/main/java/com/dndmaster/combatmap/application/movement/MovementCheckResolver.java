package com.dndmaster.combatmap.application.movement;

import java.util.Optional;

/** Adventure Runtime 판정 경계. empty이면 플레이어 입력을 기다린다. */
@FunctionalInterface
public interface MovementCheckResolver {
    Optional<MovementCheckResult> resolve(MovementCheckRequest request);

    static MovementCheckResolver pending() {
        return request -> Optional.empty();
    }
}
