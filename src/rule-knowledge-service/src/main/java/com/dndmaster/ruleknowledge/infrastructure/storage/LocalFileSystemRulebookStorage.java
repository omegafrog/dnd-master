package com.dndmaster.ruleknowledge.infrastructure.storage;

import com.dndmaster.ruleknowledge.application.registration.RulebookFileStorage;
import com.dndmaster.ruleknowledge.application.registration.StoredRulebookFile;
import com.dndmaster.ruleknowledge.domain.rulebook.RulebookId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.util.Arrays;
import java.util.Objects;

public final class LocalFileSystemRulebookStorage implements RulebookFileStorage {
    private static final Logger log = LoggerFactory.getLogger(LocalFileSystemRulebookStorage.class);
    private final Path root;

    public LocalFileSystemRulebookStorage(Path root) {
        this.root = Objects.requireNonNull(root, "root must not be null");
    }

    @Override
    public StoredRulebookFile store(RulebookId rulebookId, byte[] content) {
        Objects.requireNonNull(rulebookId, "rulebookId must not be null");
        Objects.requireNonNull(content, "content must not be null");
        Path dir = root.resolve(rulebookId.value().toString());
        Path target = dir.resolve("source.bin");
        try {
            Files.createDirectories(dir);
            Path temp = dir.resolve("source.bin.tmp");
            Files.write(temp, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            log.debug("Stored rulebook {} ({} bytes)", rulebookId.value(), content.length);
            return new StoredRulebookFile(rulebookId.value().toString());
        } catch (IOException e) {
            throw new RuntimeException("failed to store rulebook file", e);
        }
    }

    @Override
    public byte[] read(StoredRulebookFile storedFile) {
        Objects.requireNonNull(storedFile, "storedFile must not be null");
        Path file = root.resolve(storedFile.key()).resolve("source.bin");
        if (!Files.exists(file)) {
            throw new RuntimeException("stored file not found: " + storedFile.key());
        }
        try {
            byte[] bytes = Files.readAllBytes(file);
            log.debug("Read rulebook {} ({} bytes)", storedFile.key(), bytes.length);
            return Arrays.copyOf(bytes, bytes.length);
        } catch (IOException e) {
            throw new RuntimeException("failed to read rulebook file", e);
        }
    }
}
