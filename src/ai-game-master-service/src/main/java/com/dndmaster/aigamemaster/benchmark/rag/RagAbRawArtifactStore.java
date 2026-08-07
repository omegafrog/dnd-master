package com.dndmaster.aigamemaster.benchmark.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Stores response payloads and reviewer provenance outside aggregate report. */
public final class RagAbRawArtifactStore {
    private final ObjectMapper mapper;
    public RagAbRawArtifactStore(ObjectMapper mapper) { this.mapper = Objects.requireNonNull(mapper); }

    public void write(Path directory, RagAbReport report, List<RagAbReviewerRecord> reviewers) throws IOException {
        Path raw = directory.resolve("raw");
        Path provenance = directory.resolve("reviewers");
        Files.createDirectories(raw); Files.createDirectories(provenance);
        for (RagAbConditionReport condition : report.conditions()) {
            for (int index = 0; index < condition.runs().size(); index++) {
                var run = condition.runs().get(index);
                Files.writeString(raw.resolve(condition.condition().name().toLowerCase() + "-run-" + index + ".txt"), run.rawResponse());
            }
        }
        mapper.writeValue(provenance.resolve("reviewer-provenance.json").toFile(), List.copyOf(reviewers));
    }
}
