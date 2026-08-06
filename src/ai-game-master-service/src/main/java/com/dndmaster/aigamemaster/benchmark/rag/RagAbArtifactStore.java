package com.dndmaster.aigamemaster.benchmark.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public final class RagAbArtifactStore {
    private final ObjectMapper mapper;
    public RagAbArtifactStore(ObjectMapper mapper) { this.mapper = Objects.requireNonNull(mapper); }
    public Path write(Path directory, RagAbReport report) throws IOException {
        Files.createDirectories(directory);
        Path target = directory.resolve("rag-ab-report.json");
        mapper.writerWithDefaultPrettyPrinter().writeValue(target.toFile(), report);
        return target;
    }
}
