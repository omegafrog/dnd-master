package com.dndmaster.aigamemaster.retrieval;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public record RetrievalEvaluationCase(String id, String evidenceType, String query, String ownerId, String sessionId,
        String packageId, List<RetrievalReference> expected, List<RetrievalReference> alternatives,
        List<RetrievalReference> forbidden, String scope, String noEvidencePolicy, List<RetrievalReference> searchScope) {
    public RetrievalEvaluationCase(String id, String evidenceType, String query, String ownerId, String sessionId,
            String packageId, List<RetrievalReference> expected, List<RetrievalReference> alternatives,
            List<RetrievalReference> forbidden, String scope, String noEvidencePolicy) {
        this(id, evidenceType, query, ownerId, sessionId, packageId, expected, alternatives, forbidden, scope, noEvidencePolicy,
                java.util.stream.Stream.concat(expected.stream(), alternatives.stream()).toList());
    }
    public RetrievalEvaluationCase(String id, String evidenceType, String query, String ownerId, String sessionId,
            String packageId, List<RetrievalReference> expected, List<RetrievalReference> alternatives,
            List<RetrievalReference> forbidden, String scope) {
        this(id, evidenceType, query, ownerId, sessionId, packageId, expected, alternatives, forbidden, scope, "REQUIRE_EVIDENCE");
    }
    public RetrievalEvaluationCase {
        id=required(id,"id"); evidenceType=required(evidenceType,"evidenceType"); query=required(query,"query"); noEvidencePolicy=required(noEvidencePolicy,"noEvidencePolicy");
        ownerId=required(ownerId,"ownerId"); sessionId=required(sessionId,"sessionId"); packageId=required(packageId,"packageId"); scope=required(scope,"scope");
        expected=immutable(expected,"expected"); alternatives=immutable(alternatives,"alternatives"); forbidden=immutable(forbidden,"forbidden"); searchScope=immutable(searchScope,"searchScope");
        if (searchScope.isEmpty()) throw new IllegalArgumentException("search scope required");
        if (expected.isEmpty() && !"ALLOW_NO_EVIDENCE".equals(noEvidencePolicy)) throw new IllegalArgumentException("expected references required");
        var allowed=new HashSet<>(expected); allowed.addAll(alternatives); if (allowed.stream().anyMatch(forbidden::contains)) throw new IllegalArgumentException("forbidden reference cannot be relevant");
    }
    public Relevance relevance(RetrievalReference reference) { Objects.requireNonNull(reference); if (forbidden.contains(reference)) return Relevance.FORBIDDEN; return expected.contains(reference)||alternatives.contains(reference)?Relevance.RELEVANT:Relevance.IRRELEVANT; }
    private static List<RetrievalReference> immutable(List<RetrievalReference> values,String name){return List.copyOf(Objects.requireNonNull(values,name+" must not be null"));}
    private static String required(String value,String name){if(value==null||value.isBlank())throw new IllegalArgumentException(name+" required");return value.trim();}
}
