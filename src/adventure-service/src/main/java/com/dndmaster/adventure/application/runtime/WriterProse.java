package com.dndmaster.adventure.application.runtime;

/** Writer output is prose only; state deltas and tool calls have no representation here. */
public record WriterProse(String prose) {
    public WriterProse {
        if (prose == null || prose.isBlank()) throw new IllegalArgumentException("prose must not be blank");
        prose = prose.trim();
    }
}
