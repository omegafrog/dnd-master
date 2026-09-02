package com.dndmaster.adventure.application.scenario.compilation;

import com.dndmaster.adventure.domain.scenario.CandidateValidation;
import java.util.List;

public interface CandidateRepairPort {
    ResolutionCandidate repair(ResolutionCandidate candidate, List<CandidateValidation> validation);
}
