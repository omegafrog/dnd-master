package com.dndmaster.ruleknowledge;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.ruleknowledge.infrastructure.storage.LocalFileSystemRulebookStorage;
import com.dndmaster.ruleknowledge.application.registration.StoredRulebookFile;
import com.dndmaster.ruleknowledge.domain.rulebook.RulebookId;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalFileSystemRulebookStorageTest {

    @TempDir
    Path tempDir;

    @Test
    void storeAndReadRoundTrip() {
        var storage = new LocalFileSystemRulebookStorage(tempDir);
        RulebookId id = RulebookId.generate();
        byte[] content = "hello world".getBytes();

        StoredRulebookFile stored = storage.store(id, content);
        byte[] readBack = storage.read(stored);

        assertArrayEquals(content, readBack);
    }

    @Test
    void readReturnsDefensiveCopy() {
        var storage = new LocalFileSystemRulebookStorage(tempDir);
        RulebookId id = RulebookId.generate();
        byte[] content = "original".getBytes();

        StoredRulebookFile stored = storage.store(id, content);
        byte[] first = storage.read(stored);
        byte[] second = storage.read(stored);

        first[0] = 'X';
        assertArrayEquals(content, second);
    }

    @Test
    void sameIdSameContentIsIdempotent() {
        var storage = new LocalFileSystemRulebookStorage(tempDir);
        RulebookId id = RulebookId.generate();
        byte[] content = "idempotent".getBytes();

        storage.store(id, content);
        StoredRulebookFile second = storage.store(id, content);
        byte[] readBack = storage.read(second);

        assertArrayEquals(content, readBack);
    }

    @Test
    void readMissingFileThrows() {
        var storage = new LocalFileSystemRulebookStorage(tempDir);
        StoredRulebookFile missing = new StoredRulebookFile(UUID.randomUUID().toString());

        assertThrows(RuntimeException.class, () -> storage.read(missing));
    }

    @Test
    void createsDirectoriesAutomatically() {
        var storage = new LocalFileSystemRulebookStorage(tempDir);
        RulebookId id = RulebookId.generate();
        byte[] content = "test".getBytes();

        storage.store(id, content);

        assertTrue(Files.exists(tempDir.resolve(id.value().toString()).resolve("source.bin")));
    }
}
