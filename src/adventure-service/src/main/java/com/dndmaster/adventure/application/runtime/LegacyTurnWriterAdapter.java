package com.dndmaster.adventure.application.runtime;

/** Compatibility writer used while the existing planner adapter is migrated. */
public final class LegacyTurnWriterAdapter implements TurnWriterPort {
    @Override
    public WriterProse write(WriterContext context) {
        String prose = context.visibleFacts().stream()
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse("The moment passes without further detail.");
        return new WriterProse(prose);
    }
}
