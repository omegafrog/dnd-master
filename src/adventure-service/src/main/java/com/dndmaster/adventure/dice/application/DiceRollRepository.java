package com.dndmaster.adventure.dice.application;

import com.dndmaster.adventure.dice.domain.DiceRoll;
import java.util.Optional;
import java.util.UUID;

public interface DiceRollRepository {
    Optional<DiceRoll> findByCommandId(UUID commandId);
    void save(DiceRoll roll);
}
