package com.dndmaster.adventure.application.runtime;

import java.util.List;

public interface PlanCandidatePort {
    List<PlanCandidate> generate(PlanningContext context, int candidateCount);
}
