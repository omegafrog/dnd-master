package com.dndmaster.adventure.application.runtime;

import java.util.Objects;

public record ExemplarResult(StyleExemplar exemplar, double metadataScore, double semanticScore,
        double rerankScore, int rank) {
    public ExemplarResult {
        exemplar = Objects.requireNonNull(exemplar, "exemplar must not be null");
        if (rank <= 0) throw new IllegalArgumentException("rank must be positive");
    }
    public Provenance provenance() { return exemplar.provenance(); }
}
