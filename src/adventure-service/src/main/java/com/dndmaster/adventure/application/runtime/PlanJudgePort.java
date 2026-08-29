package com.dndmaster.adventure.application.runtime;

import java.util.List;

public interface PlanJudgePort {
    List<PlanSelection.Score> judge(List<PlanCandidate> candidates, PlanningContext context);
}
