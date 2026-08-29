package com.dndmaster.gmeval.registry;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/** Small durable adapter for local/operator registry storage. */
public final class JsonPromptRegistryStore implements PromptRegistryStore {
    private final Path path;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules().enable(SerializationFeature.INDENT_OUTPUT);

    public JsonPromptRegistryStore(Path path) {
        this.path = path.toAbsolutePath();
    }

    @Override
    public List<PromptArtifact> load() {
        if (!Files.exists(path)) return List.of();
        try {
            if (Files.size(path) == 0) return List.of();
            return List.copyOf(mapper.readValue(path.toFile(), mapper.getTypeFactory()
                    .constructCollectionType(List.class, PromptArtifact.class)));
        } catch (IOException | RuntimeException failure) {
            throw new IllegalArgumentException("could not read prompt registry", failure);
        }
    }

    @Override
    public void save(List<PromptArtifact> artifacts) {
        try {
            Path parent = path.getParent() == null ? Path.of(".").toAbsolutePath() : path.getParent();
            Files.createDirectories(parent);
            Path temp = Files.createTempFile(parent, path.getFileName().toString(), ".tmp");
            try {
                mapper.writeValue(temp.toFile(), artifacts);
                try {
                    Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException unsupported) {
                    Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temp);
            }
        } catch (IOException failure) {
            throw new IllegalArgumentException("could not write prompt registry", failure);
        }
    }
}
