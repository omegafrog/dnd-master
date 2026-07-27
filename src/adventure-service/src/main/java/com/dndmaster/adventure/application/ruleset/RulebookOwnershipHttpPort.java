package com.dndmaster.adventure.application.ruleset;

import com.dndmaster.adventure.domain.ruleset.OwnerPlayerId;
import com.dndmaster.adventure.domain.ruleset.RulebookId;

public interface RulebookOwnershipHttpPort {
    boolean isOwnedBy(RulebookId rulebookId, OwnerPlayerId ownerPlayerId);
}
