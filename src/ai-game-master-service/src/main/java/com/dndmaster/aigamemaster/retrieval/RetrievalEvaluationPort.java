package com.dndmaster.aigamemaster.retrieval;

@FunctionalInterface public interface RetrievalEvaluationPort {
    RetrievalEvaluationResult retrieve(RetrievalEvaluationCase evaluationCase, int limit);

    default void validateScope(RetrievalEvaluationCase evaluationCase) {
        retrieve(evaluationCase, 0);
    }
}
