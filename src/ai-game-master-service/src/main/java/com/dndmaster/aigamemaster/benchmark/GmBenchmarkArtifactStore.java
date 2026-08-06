package com.dndmaster.aigamemaster.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class GmBenchmarkArtifactStore {
    private final ObjectMapper mapper;

    public GmBenchmarkArtifactStore(ObjectMapper mapper) { this.mapper = mapper; }

    public void write(Path directory, GmBenchmarkReport report) throws IOException {
        Path raw = directory.resolve("raw");
        Files.createDirectories(raw);
        for (GmBenchmarkRun run : report.runs()) {
            Path artifact = raw.resolve(run.caseId().replace(':', '_') + "-run-" + run.runIndex() + ".json");
            mapper.writeValue(artifact.toFile(), run);
        }
        mapper.writeValue(directory.resolve("baseline-report.json").toFile(), report);
    }
}
