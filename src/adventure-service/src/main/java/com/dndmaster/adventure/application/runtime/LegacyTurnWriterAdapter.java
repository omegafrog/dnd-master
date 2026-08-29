package com.dndmaster.adventure.application.runtime;

/** Compatibility writer used while the existing planner adapter is migrated. */
public final class LegacyTurnWriterAdapter implements TurnWriterPort {
    @Override
    public WriterProse write(WriterContext context) {
        ResolvedTurnPlan resolved = context.resolvedPlan();
        String prose = resolved.plan().revealableFacts().stream()
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(resolved.plan().judgment());
        return new WriterProse(prose);
    }
}
