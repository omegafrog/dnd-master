package com.dndmaster.aigamemaster.retrieval;

import java.util.Objects;

public final class RetrievalEvaluationRouter implements RetrievalEvaluationPort {
    private final RetrievalEvaluationPort rule;
    private final RetrievalEvaluationPort story;
    public RetrievalEvaluationRouter(RetrievalEvaluationPort rule, RetrievalEvaluationPort story) { this.rule = Objects.requireNonNull(rule); this.story = Objects.requireNonNull(story); }
    @Override public RetrievalEvaluationResult retrieve(RetrievalEvaluationCase c, int limit) { return "rule".equals(c.evidenceType()) ? rule.retrieve(c, limit) : story.retrieve(c, limit); }
}
