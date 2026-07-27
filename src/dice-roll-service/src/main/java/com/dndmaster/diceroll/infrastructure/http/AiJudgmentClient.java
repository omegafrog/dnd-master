package com.dndmaster.diceroll.infrastructure.http;

import com.dndmaster.diceroll.domain.DiceRoll;

public interface AiJudgmentClient {
    void deliver(String idempotencyKey, DiceRoll roll);
}
