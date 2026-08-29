package com.dndmaster.gmeval.optimization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/** Atomic local persistence adapter for immutable optimization reports. */
public final class JsonPromptOptimizationRunStore implements PromptOptimizationRunStore {
    private final Path path;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules().enable(SerializationFeature.INDENT_OUTPUT);

    public JsonPromptOptimizationRunStore(Path path) {
        this.path = path.toAbsolutePath();
    }

    @Override
    public List<PromptOptimizationRun> load() {
        if (!Files.exists(path)) return List.of();
        try {
            if (Files.size(path) == 0) return List.of();
            return List.copyOf(mapper.readValue(path.toFile(), mapper.getTypeFactory()
                    .constructCollectionType(List.class, PromptOptimizationRun.class)));
        } catch (IOException | RuntimeException failure) {
            throw new IllegalArgumentException("could not read prompt optimization runs", failure);
        }
    }

    @Override
    public void save(List<PromptOptimizationRun> runs) {
        try {
            Path parent = path.getParent() == null ? Path.of(".").toAbsolutePath() : path.getParent();
            Files.createDirectories(parent);
            Path temp = Files.createTempFile(parent, path.getFileName().toString(), ".tmp");
            try {
                mapper.writeValue(temp.toFile(), runs);
                try {
                    Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException unsupported) {
                    Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temp);
            }
        } catch (IOException failure) {
            throw new IllegalArgumentException("could not write prompt optimization runs", failure);
        }
    }
}
