package com.dndmaster.adventure.application.combat;

import java.util.UUID;

/** 전투 종료 조건을 만족했을 때 종료 처리를 맡기는 경계입니다. */
@FunctionalInterface
public interface CombatEndPort {
    CombatEndResult endWhenEnemiesDefeated(UUID adventureId);
}
