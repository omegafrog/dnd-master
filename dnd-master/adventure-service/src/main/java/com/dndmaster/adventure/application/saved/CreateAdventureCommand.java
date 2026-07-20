package com.dndmaster.adventure.application.saved;

import com.dndmaster.adventure.domain.adventure.AdventureContext;
import com.dndmaster.adventure.domain.adventure.CharacterSheetId;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import com.dndmaster.adventure.domain.adventure.RuleSetId;
import com.dndmaster.adventure.domain.adventure.ScenarioId;
import java.util.Objects;

public record CreateAdventureCommand(
        OwnerPlayerId ownerPlayerId, ScenarioId scenarioId, RuleSetId ruleSetId,
        CharacterSheetId characterSheetId, AdventureContext initialContext) {
    public CreateAdventureCommand {
        Objects.requireNonNull(ownerPlayerId, "owner must not be null");
        Objects.requireNonNull(scenarioId, "scenario must not be null");
        Objects.requireNonNull(ruleSetId, "rule set must not be null");
        Objects.requireNonNull(characterSheetId, "character sheet must not be null");
        Objects.requireNonNull(initialContext, "initial context must not be null");
    }
}
