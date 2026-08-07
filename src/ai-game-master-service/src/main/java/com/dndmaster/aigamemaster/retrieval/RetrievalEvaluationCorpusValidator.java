package com.dndmaster.aigamemaster.retrieval;

import java.util.HashSet;

public final class RetrievalEvaluationCorpusValidator {
    private RetrievalEvaluationCorpusValidator() {}

    public static void validate(RetrievalEvaluationCorpus corpus) {
        for (var evaluationCase : corpus.cases()) {
            var searchScope = new HashSet<>(evaluationCase.searchScope());
            for (var reference : evaluationCase.expected()) requireInScope(evaluationCase, searchScope, reference);
            for (var reference : evaluationCase.alternatives()) requireInScope(evaluationCase, searchScope, reference);
            for (var reference : evaluationCase.forbidden()) requireInScope(evaluationCase, searchScope, reference);
        }
    }

    private static void requireInScope(RetrievalEvaluationCase evaluationCase,
            HashSet<RetrievalReference> searchScope, RetrievalReference reference) {
        if (!searchScope.contains(reference)) {
            throw new IllegalArgumentException("case " + evaluationCase.id()
                    + " reference is absent from search scope: " + reference);
        }
    }
}
