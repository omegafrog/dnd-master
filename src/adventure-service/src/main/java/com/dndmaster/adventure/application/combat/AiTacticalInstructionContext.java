package com.dndmaster.adventure.application.combat;

import java.util.List;

/** Typed P1 tactical guidance; provider prompt strings do not cross this boundary. */
public record AiTacticalInstructionContext(String instruction, List<String> constraints) {
    public AiTacticalInstructionContext {
        if (instruction == null || instruction.isBlank()) throw new IllegalArgumentException("tactical instruction must not be blank");
        instruction = instruction.trim();
        constraints = List.copyOf(constraints == null ? List.of() : constraints);
    }

    public AiTacticalInstructionContext(String instruction) { this(instruction, List.of()); }

    public static AiTacticalInstructionContext none() { return new AiTacticalInstructionContext("No tactical instruction", List.of()); }
}
