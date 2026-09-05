package com.dndmaster.adventure.domain.combat;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

/** A player declaration that is intentionally independent from the structured action catalog. */
public record FreeFormActionDeclaration(UUID actorId, String text) {
    public static final int MAX_UTF8_BYTES = 4 * 1024;

    public FreeFormActionDeclaration {
        Objects.requireNonNull(actorId, "actor id must not be null");
        if (text == null || text.isBlank()) throw new IllegalArgumentException("free-form declaration must not be blank");
        if (text.chars().anyMatch(character -> character < 0x20 || character == 0x7f)) {
            throw new IllegalArgumentException("free-form declaration contains a control character");
        }
        text = text.trim();
        if (text.getBytes(StandardCharsets.UTF_8).length > MAX_UTF8_BYTES) {
            throw new IllegalArgumentException("free-form declaration exceeds 4 KiB");
        }
    }
}
