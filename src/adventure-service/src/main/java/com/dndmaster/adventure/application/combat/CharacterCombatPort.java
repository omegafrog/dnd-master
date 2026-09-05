package com.dndmaster.adventure.application.combat;

public interface CharacterCombatPort {
    void requireUsableCharacter(CombatActionCommand command);

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
