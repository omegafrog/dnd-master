package com.dndmaster.diceroll.api;

import com.dndmaster.diceroll.domain.DiceRoll;
import java.util.List;
import java.util.UUID;

public record DiceRollResponse(UUID rollId, String scope, List<Integer> faces, int total) {
    public static DiceRollResponse from(DiceRoll roll) {
        var result = roll.result().orElseThrow();
        return new DiceRollResponse(roll.id().value(), roll.scope().name(), result.faces(), result.total());
    }
}
