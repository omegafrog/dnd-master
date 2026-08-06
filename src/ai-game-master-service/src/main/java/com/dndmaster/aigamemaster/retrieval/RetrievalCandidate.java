package com.dndmaster.aigamemaster.retrieval;

public record RetrievalCandidate(RetrievalReference reference, String ownerId, String sessionId, String packageId, double score) {
    public RetrievalCandidate { if(reference==null||ownerId==null||sessionId==null||packageId==null||ownerId.isBlank()||sessionId.isBlank()||packageId.isBlank()||!Double.isFinite(score)||score<0) throw new IllegalArgumentException("invalid candidate"); }
    public boolean scopeMatches(RetrievalEvaluationCase c){return ownerId.equals(c.ownerId())&&sessionId.equals(c.sessionId())&&packageId.equals(c.packageId());}
}

