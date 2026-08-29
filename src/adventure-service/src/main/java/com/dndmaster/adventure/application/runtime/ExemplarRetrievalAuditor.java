package com.dndmaster.adventure.application.runtime;

import java.util.List;

public final class ExemplarRetrievalAuditor implements ExemplarRetrieverPort {
    private final ExemplarRetrieverPort delegate;
    private final ExemplarRetrievalAuditPort audit;
    private final String model;
    public ExemplarRetrievalAuditor(ExemplarRetrieverPort delegate, ExemplarRetrievalAuditPort audit, String model) {
        this.delegate = java.util.Objects.requireNonNull(delegate); this.audit = java.util.Objects.requireNonNull(audit);
        this.model = model == null ? "" : model;
    }
    @Override public List<ExemplarResult> retrieve(ExemplarQuery query) {
        long started = System.nanoTime();
        try {
            List<ExemplarResult> results = delegate.retrieve(query);
            audit.append(new ExemplarRetrievalAudit(query.semanticQuery(), query.limit(), results.stream().map(r -> r.exemplar().id()).toList(),
                    results.stream().map(ExemplarResult::rerankScore).toList(), model, (System.nanoTime() - started) / 1_000_000));
            return results;
        } catch (RuntimeException failure) {
            audit.append(new ExemplarRetrievalAudit(query.semanticQuery(), query.limit(), List.of(), List.of(), model,
                    (System.nanoTime() - started) / 1_000_000));
            return List.of();
        }
    }
}
