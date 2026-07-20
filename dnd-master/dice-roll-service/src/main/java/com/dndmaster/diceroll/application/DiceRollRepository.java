package com.dndmaster.diceroll.application;

import com.dndmaster.diceroll.domain.DiceRoll;

public interface DiceRollRepository {
    void save(DiceRoll roll);
}
