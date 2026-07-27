package com.dndmaster.adventure.application.guidance;

import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import com.dndmaster.adventure.domain.adventure.RuleSetId;

public interface RuleSetSearchScopePort {
    RuleSearchScope resolve(AdventureId adventureId, RuleSetId ruleSetId, OwnerPlayerId requestingOwner);
}
