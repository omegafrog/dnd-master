package com.dndmaster.aigamemaster.retrieval;

import java.util.HashSet;
import java.util.List;

public record RetrievalEvaluationCorpus(String version, List<RetrievalEvaluationCase> cases) {
    public RetrievalEvaluationCorpus {
        if (version == null || version.isBlank() || cases == null || cases.isEmpty()) throw new IllegalArgumentException("invalid corpus");
        cases = List.copyOf(cases);
        if (new HashSet<>(cases.stream().map(RetrievalEvaluationCase::id).toList()).size() != cases.size()) throw new IllegalArgumentException("case ids must be unique");
    }
    public String caseIdentity(RetrievalEvaluationCase c) { if (!cases.contains(c)) throw new IllegalArgumentException("case not in corpus"); return version + ":" + c.id(); }
}
