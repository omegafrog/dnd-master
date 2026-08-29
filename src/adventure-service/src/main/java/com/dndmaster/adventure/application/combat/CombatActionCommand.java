package com.dndmaster.adventure.application.combat;

import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.CharacterSheetId;
import com.dndmaster.adventure.domain.adventure.RuleSetId;
import java.util.Objects;
import java.util.UUID;

public record CombatActionCommand(
        UUID operationId, AdventureId adventureId, UUID sessionId, RuleSetId ruleSetId, CharacterSheetId characterSheetId, UUID combatMapId,
        CombatActorRole role, String action, String movementPath, UUID ownerPlayerId, UUID tokenId, long expectedVersion,
        Integer targetArmorClass, Integer attackModifier, CharacterSheetId targetCharacterSheetId, Integer damageAmount, boolean endCombat) {
    public CombatActionCommand {
        Objects.requireNonNull(operationId, "operation id must not be null");
        Objects.requireNonNull(adventureId, "adventure id must not be null");
        Objects.requireNonNull(sessionId, "session id must not be null");
        Objects.requireNonNull(ruleSetId, "rule set id must not be null");
        Objects.requireNonNull(characterSheetId, "character sheet id must not be null");
        Objects.requireNonNull(role, "role must not be null");
        if (action == null || action.isBlank()) throw new IllegalArgumentException("action must not be blank");
        action = action.trim();
        movementPath = movementPath == null || movementPath.isBlank() ? null : movementPath.trim();
        if (damageAmount != null && (damageAmount < 1 || damageAmount > 1000)) throw new IllegalArgumentException("damage amount must be between 1 and 1000");
    }
    public CombatActionCommand(UUID operationId, AdventureId adventureId, UUID sessionId, RuleSetId ruleSetId, CharacterSheetId characterSheetId, UUID combatMapId,
            CombatActorRole role, String action, String movementPath, UUID ownerPlayerId, UUID tokenId, long expectedVersion) {
        this(operationId, adventureId, sessionId, ruleSetId, characterSheetId, combatMapId, role, action, movementPath, ownerPlayerId, tokenId, expectedVersion, null, null, null, null, false);
    }

    public CombatActionCommand(
            UUID operationId, AdventureId adventureId, RuleSetId ruleSetId, CharacterSheetId characterSheetId,
            CombatActorRole role, String action, String movementPath) {
        this(operationId, adventureId, adventureId.value(), ruleSetId, characterSheetId, null, role, action, movementPath, null, null, 0L);
    }

    public CombatActionCommand(
            UUID operationId, AdventureId adventureId, RuleSetId ruleSetId, CharacterSheetId characterSheetId,
            UUID combatMapId, CombatActorRole role, String action, String movementPath,
            UUID ownerPlayerId, UUID tokenId, long expectedVersion) {
        this(operationId, adventureId, adventureId.value(), ruleSetId, characterSheetId, combatMapId, role, action,
                movementPath, ownerPlayerId, tokenId, expectedVersion);
    }

    public String fingerprint() {
        return adventureId + "|" + sessionId + "|" + ruleSetId + "|" + characterSheetId + "|" + combatMapId + "|" + role + "|" + action
                + "|" + movementPath + "|" + ownerPlayerId + "|" + tokenId + "|" + expectedVersion + "|" + targetArmorClass + "|" + attackModifier + "|" + targetCharacterSheetId + "|" + damageAmount + "|" + endCombat;
    }
}
