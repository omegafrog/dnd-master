package com.dndmaster.gmeval.registry;

import java.util.Objects;

public record PromptVersion(PromptRole role, String value) {
    public PromptVersion {
        role = Objects.requireNonNull(role, "prompt role required");
        if (value == null || value.isBlank()) throw new IllegalArgumentException("prompt version required");
        value = value.trim();
    }
}
