package com.dndmaster.adventure.domain.scenario;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record SpellDefinitionEditHistory(long revision, String definitionId, UUID editorId,
        Instant changedAt, StructuredSpellDefinition previous, StructuredSpellDefinition current) {
    public SpellDefinitionEditHistory {
        if (revision < 1) throw new IllegalArgumentException("spell definition revision must be positive");
        if (definitionId == null || definitionId.isBlank()) throw new IllegalArgumentException("definition id must not be blank");
        Objects.requireNonNull(editorId, "editor id must not be null");
        Objects.requireNonNull(changedAt, "change time must not be null");
        Objects.requireNonNull(previous, "previous definition must not be null");
        Objects.requireNonNull(current, "current definition must not be null");
    }
}
