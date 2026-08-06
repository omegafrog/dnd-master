package com.dndmaster.aigamemaster.retrieval;

import java.util.List;

public record RetrievalEvaluationResult(String caseId,List<RetrievalCandidate> candidates,double latencyMs){
    public RetrievalEvaluationResult {if(caseId==null||caseId.isBlank()||candidates==null||!Double.isFinite(latencyMs)||latencyMs<0)throw new IllegalArgumentException("invalid retrieval result");candidates=List.copyOf(candidates);}
}

