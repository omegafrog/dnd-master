package com.dndmaster.gmeval.tuning;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/** Atomic JSON persistence for provider artifacts and hyperparameters. */
public final class JsonTrainingArtifactStore implements TrainingArtifactStore {
    private final Path path;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules().enable(SerializationFeature.INDENT_OUTPUT);

    public JsonTrainingArtifactStore(Path path) { this.path = path.toAbsolutePath(); }

    @Override public List<TrainingArtifact> load() {
        if (!Files.exists(path)) return List.of();
        try {
            if (Files.size(path) == 0) return List.of();
            return List.copyOf(mapper.readValue(path.toFile(), mapper.getTypeFactory()
                    .constructCollectionType(List.class, TrainingArtifact.class)));
        } catch (IOException | RuntimeException failure) {
            throw new IllegalArgumentException("could not read training artifacts", failure);
        }
    }

    @Override public void save(List<TrainingArtifact> artifacts) {
        try {
            Path parent = path.getParent() == null ? Path.of(".").toAbsolutePath() : path.getParent();
            Files.createDirectories(parent);
            Path temp = Files.createTempFile(parent, path.getFileName().toString(), ".tmp");
            try {
                mapper.writeValue(temp.toFile(), artifacts);
                try { Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
                catch (AtomicMoveNotSupportedException unsupported) { Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING); }
            } finally { Files.deleteIfExists(temp); }
        } catch (IOException failure) {
            throw new IllegalArgumentException("could not write training artifacts", failure);
        }
    }
}
