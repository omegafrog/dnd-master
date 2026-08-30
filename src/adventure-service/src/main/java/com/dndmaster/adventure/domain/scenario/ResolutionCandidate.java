package com.dndmaster.adventure.domain.scenario;

import java.util.List;

public interface ResolutionCandidate {
    ResolutionKind kind();
    String abilityOrSkill();
    Integer dc();
    String diceExpression();
    ResolutionVisibility visibility();
    String sourceQuote();
    List<ScenarioSourceReference> sourceRefs();
    String provenance();
    ScenarioResolutionDetail detail();

    default String candidateKey() {
        return kind() == null ? "candidate" : kind().name();
    }

    default boolean required() {
        return true;
    }
}
