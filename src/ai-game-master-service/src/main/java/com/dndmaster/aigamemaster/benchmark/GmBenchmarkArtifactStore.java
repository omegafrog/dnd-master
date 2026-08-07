package com.dndmaster.aigamemaster.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class GmBenchmarkArtifactStore {
    private final ObjectMapper mapper;

    public GmBenchmarkArtifactStore(ObjectMapper mapper) { this.mapper = mapper; }

    public void write(Path directory, GmBenchmarkReport report) throws IOException {
        report.assertPublishable();
        Path raw = directory.resolve("raw");
        Files.createDirectories(raw);
        for (GmBenchmarkRun run : report.runs()) {
            String model = safeSegment(report.model());
            String caseId = safeSegment(run.caseId());
            Path artifact = raw.resolve(model + "-" + caseId + "-run-" + run.runIndex() + ".json");
            if (!artifact.normalize().startsWith(raw.normalize())) throw new IOException("unsafe benchmark artifact path");
            mapper.writeValue(artifact.toFile(), run);
        }
        mapper.writeValue(directory.resolve("baseline-report.json").toFile(), report);
    }

    private static String safeSegment(String value) {
        if (value == null || value.isBlank() || !value.matches("[A-Za-z0-9._:-]+") || value.contains("..")) {
            throw new IllegalArgumentException("unsafe artifact identity");
        }
        return value.replace(':', '_');
    }
}
