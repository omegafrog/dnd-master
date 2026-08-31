package com.dndmaster.diceroll.application;

import com.dndmaster.diceroll.domain.DiceRoll;
import java.util.Optional;
import java.util.UUID;

public interface DiceRollRepository {
    Optional<DiceRoll> findByCommandId(UUID commandId);
    void save(DiceRoll roll);
}
