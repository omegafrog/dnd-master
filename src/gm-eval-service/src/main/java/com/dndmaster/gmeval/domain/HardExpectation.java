package com.dndmaster.gmeval.domain;

public sealed interface HardExpectation permits HardExpectation.ForbiddenFact, HardExpectation.RequiredFact,
        HardExpectation.RuleContradiction, HardExpectation.StateMutation, HardExpectation.AgencyViolation,
        HardExpectation.Unsupported {
    String category(); String id();
    record ForbiddenFact(String category, String id, String fact) implements HardExpectation { public ForbiddenFact { required(category); required(id); required(fact); } }
    record RequiredFact(String category, String id, String fact) implements HardExpectation { public RequiredFact { required(category); required(id); required(fact); } }
    record RuleContradiction(String category, String id, String authoritativeRule) implements HardExpectation { public RuleContradiction { required(category); required(id); required(authoritativeRule); } }
    record StateMutation(String category, String id, String stateFact, String expectedValue) implements HardExpectation { public StateMutation { required(category); required(id); required(stateFact); required(expectedValue); } }
    record AgencyViolation(String category, String id, String voluntaryAction) implements HardExpectation { public AgencyViolation { required(category); required(id); required(voluntaryAction); } }
    record Unsupported(String category, String id, String reason) implements HardExpectation { public Unsupported { required(category); required(id); required(reason); } }
    private static void required(String s) { if (s == null || s.isBlank()) throw new IllegalArgumentException("expectation field required"); }
}
