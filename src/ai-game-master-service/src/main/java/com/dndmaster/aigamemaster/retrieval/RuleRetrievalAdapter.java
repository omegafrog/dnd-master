package com.dndmaster.aigamemaster.retrieval;

import java.util.Objects;

public final class RuleRetrievalAdapter implements RetrievalEvaluationPort {
    private final RetrievalEvaluationPort delegate;
    public RuleRetrievalAdapter(RetrievalEvaluationPort delegate) { this.delegate = Objects.requireNonNull(delegate); }
    @Override public RetrievalEvaluationResult retrieve(RetrievalEvaluationCase evaluationCase, int limit) {
        if (!"rule".equals(evaluationCase.evidenceType())) throw new IllegalArgumentException("rule adapter received non-rule case");
        return delegate.retrieve(evaluationCase, limit);
    }
}
