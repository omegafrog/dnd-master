package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.adventure.InitialSourceContextCandidate;
import com.dndmaster.adventure.domain.scenario.ScenarioPackage;
import java.util.List;

public interface InitialSourceContextProposalPort {
    InitialSourceContextProposalResult propose(ScenarioPackage scenarioPackage, List<InitialSourceContextCandidate> candidates);

    record InitialSourceContextProposalResult(String status, List<InitialSourceContextCandidate> candidates) {
        public InitialSourceContextProposalResult {
            candidates = List.copyOf(candidates);
        }
    }
}
