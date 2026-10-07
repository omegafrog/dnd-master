package com.dndmaster.adventure.application.combat;

public interface CharacterCombatPort {
    void requireUsableCharacter(CombatActionCommand command);

    default CharacterCombatStatus combatStatus(CombatActionCommand command) { return null; }

    default void applyDeathSavingThrow(CombatActionCommand command, int roll) {}

    default com.dndmaster.adventure.domain.combat.CombatSpellcastingProfile spellcastingProfile(CombatActionCommand command) {
        return com.dndmaster.adventure.domain.combat.CombatSpellcastingProfile.empty();
    }

    default void consumeSpellSlot(CombatActionCommand command, int slotLevel) {
        throw new UnsupportedOperationException("spell slot consumption is unavailable");
    }

    /** Returns the name that may be shown for a party member in the encounter order. */
    default String displayName(java.util.UUID characterSheetId, java.util.UUID ownerPlayerId, java.util.UUID sessionId) {
        return characterSheetId.toString();
    }

    /** Returns the character sheet's basic melee attack bonus for the selected attack action. */
    default Integer attackModifier(CombatActionCommand command) { return null; }

    /** Returns a target character sheet's armor class for an AI attack. */
    default Integer armorClass(CombatActionCommand command,
            com.dndmaster.adventure.domain.adventure.CharacterSheetId targetCharacterSheetId) { return null; }

    /** Returns the average damage of the character sheet's first listed attack. */
    default Integer damageAmount(CombatActionCommand command) { return null; }

    /** Returns the dexterity modifier used for the character's initiative roll. */
    default int initiativeModifier(java.util.UUID characterSheetId, java.util.UUID ownerPlayerId,
            java.util.UUID sessionId) { return 0; }

    /**
     * Gives the owning Character service a terminal, idempotent commit boundary.
     * Existing action mutations remain the authoritative state; this hook must
     * never copy character data into Adventure Runtime.
     */
    default void commitFinalState(CombatFinalizationCommand command) {}

    /**
     * Applies only the structured mechanical effects returned by adjudication.
     *
     * The default keeps existing character adapters source compatible. Adapters
     * that own character persistence should make this operation idempotent by
     * command/operation id before changing a sheet.
     */
    default void applyOutcome(CombatActionCommand command, CombatOutcome outcome) {}
}
