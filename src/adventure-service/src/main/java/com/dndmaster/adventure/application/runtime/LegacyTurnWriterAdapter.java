package com.dndmaster.adventure.application.runtime;

/** Compatibility writer used while the existing planner adapter is migrated. */
public final class LegacyTurnWriterAdapter implements TurnWriterPort {
    @Override
    public WriterProse write(PlayerVisibleTurn turn) {
        String prose = turn.visibleFacts().stream()
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(turn.narrationSeed().isBlank() ? "The moment passes without further detail." : turn.narrationSeed());
        return new WriterProse(prose);
    }
}
