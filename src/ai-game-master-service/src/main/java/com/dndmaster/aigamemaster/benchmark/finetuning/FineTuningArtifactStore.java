package com.dndmaster.aigamemaster.benchmark.finetuning;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/** Persists only metadata; loading never silently accepts an unidentifiable artifact. */
public final class FineTuningArtifactStore {
    private final ObjectMapper mapper;

    public FineTuningArtifactStore(ObjectMapper mapper) { this.mapper = Objects.requireNonNull(mapper); }

    public Path write(Path directory, FineTuningModelArtifact artifact) throws IOException {
        Objects.requireNonNull(directory); Objects.requireNonNull(artifact);
        Files.createDirectories(directory);
        Path target = directory.resolve(artifact.variant().name().toLowerCase() + "-artifact.json");
        mapper.writerWithDefaultPrettyPrinter().writeValue(target.toFile(), artifact);
        return target;
    }

    public FineTuningModelArtifact read(Path file) throws IOException {
        return mapper.readValue(Objects.requireNonNull(file).toFile(), FineTuningModelArtifact.class);
    }
}
