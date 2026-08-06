package com.dndmaster.aigamemaster.retrieval;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class RetrievalEvaluationArtifactStore {
    private final ObjectMapper mapper;
    public RetrievalEvaluationArtifactStore(ObjectMapper mapper) { this.mapper = mapper; }
    public Path write(Path directory, RetrievalEvaluationReport report) throws IOException {
        Files.createDirectories(directory);
        Path target = directory.resolve("retrieval-evaluation-report.json");
        mapper.writerWithDefaultPrettyPrinter().writeValue(target.toFile(), report);
        return target;
    }
}
