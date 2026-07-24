package com.dndmaster.adventure.application.combat;

import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.CharacterSheetId;
import com.dndmaster.adventure.domain.adventure.RuleSetId;
import java.util.Objects;
import java.util.UUID;

public record CombatActionCommand(
        UUID operationId, AdventureId adventureId, RuleSetId ruleSetId, CharacterSheetId characterSheetId, UUID combatMapId,
        CombatActorRole role, String action, String movementPath, UUID ownerPlayerId, UUID tokenId, long expectedVersion) {
    public CombatActionCommand {
        Objects.requireNonNull(operationId, "operation id must not be null");
        Objects.requireNonNull(adventureId, "adventure id must not be null");
        Objects.requireNonNull(ruleSetId, "rule set id must not be null");
        Objects.requireNonNull(characterSheetId, "character sheet id must not be null");
        Objects.requireNonNull(role, "role must not be null");
        if (action == null || action.isBlank()) throw new IllegalArgumentException("action must not be blank");
        action = action.trim();
        movementPath = movementPath == null || movementPath.isBlank() ? null : movementPath.trim();
    }

    public CombatActionCommand(
            UUID operationId, AdventureId adventureId, RuleSetId ruleSetId, CharacterSheetId characterSheetId,
            CombatActorRole role, String action, String movementPath) {
        this(operationId, adventureId, ruleSetId, characterSheetId, null, role, action, movementPath, null, null, 0L);
    }

    public String fingerprint() {
        return adventureId + "|" + ruleSetId + "|" + characterSheetId + "|" + combatMapId + "|" + role + "|" + action
                + "|" + movementPath + "|" + ownerPlayerId + "|" + tokenId + "|" + expectedVersion;
    }
}
