package com.dndmaster.adventure.application.runtime;

import java.util.List;
import java.util.Objects;

public record ExemplarRetrievalAudit(String query, int requestedLimit, List<String> retrievalIds,
        List<Double> scores, String model, long latencyMillis) {
    public ExemplarRetrievalAudit {
        query = query == null ? "" : query.trim();
        retrievalIds = List.copyOf(Objects.requireNonNull(retrievalIds));
        scores = List.copyOf(Objects.requireNonNull(scores));
        model = model == null ? "" : model.trim();
        if (requestedLimit <= 0 || latencyMillis < 0) throw new IllegalArgumentException("invalid exemplar audit bounds");
    }
}
