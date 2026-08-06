package com.dndmaster.aigamemaster.retrieval;

import java.util.Objects;

public final class StoryRetrievalAdapter implements RetrievalEvaluationPort {
    private final RetrievalEvaluationPort delegate;
    public StoryRetrievalAdapter(RetrievalEvaluationPort delegate) { this.delegate = Objects.requireNonNull(delegate); }
    @Override public RetrievalEvaluationResult retrieve(RetrievalEvaluationCase evaluationCase, int limit) {
        if ("rule".equals(evaluationCase.evidenceType())) throw new IllegalArgumentException("story adapter received rule case");
        return delegate.retrieve(evaluationCase, limit);
    }
}
