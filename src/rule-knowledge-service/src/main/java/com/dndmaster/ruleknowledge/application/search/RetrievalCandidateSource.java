package com.dndmaster.ruleknowledge.application.search;

@FunctionalInterface
public interface RetrievalCandidateSource {
    java.util.List<HybridRetrievalCandidate> search(String query, RetrievalScope scope, int limit);
}
