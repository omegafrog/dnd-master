package com.dndmaster.character.application;

import java.util.Objects;

/** Idempotent consumer. Retry caller may safely deliver the same event again. */
public final class CharacterSheetsDeletionConsumer {
    private final CharacterSheetRepository repository;

    public CharacterSheetsDeletionConsumer(CharacterSheetRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
    }

    public void consume(CharacterSheetsDeletionRequested event) {
        for (var id : event.characterSheetIds()) repository.deleteById(new com.dndmaster.character.domain.CharacterSheetId(id));
    }

    public void consumeWithRetry(CharacterSheetsDeletionRequested event, int maxAttempts) {
        if (maxAttempts < 1) throw new IllegalArgumentException("max attempts must be positive");
        RuntimeException failure = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try { consume(event); return; } catch (RuntimeException exception) { failure = exception; }
        }
        throw failure;
    }
}
