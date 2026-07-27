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
}
