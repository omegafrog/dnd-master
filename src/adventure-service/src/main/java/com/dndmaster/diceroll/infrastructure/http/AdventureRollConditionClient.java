package com.dndmaster.diceroll.infrastructure.http;

import com.dndmaster.diceroll.domain.AdventureId;
import com.dndmaster.diceroll.domain.RollScope;
import com.dndmaster.diceroll.domain.RuleSetId;

public interface AdventureRollConditionClient {
    void requireAllowed(AdventureId adventureId, RuleSetId ruleSetId, RollScope scope);
}
