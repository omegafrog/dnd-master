package com.dndmaster.adventure.application.combat;

public interface CharacterCombatPort {
    void requireUsableCharacter(CombatActionCommand command);

    /**
     * Applies only the structured mechanical effects returned by adjudication.
     *
     * The default keeps existing character adapters source compatible. Adapters
     * that own character persistence should make this operation idempotent by
     * command/operation id before changing a sheet.
     */
    default void applyOutcome(CombatActionCommand command, CombatOutcome outcome) {}
}
