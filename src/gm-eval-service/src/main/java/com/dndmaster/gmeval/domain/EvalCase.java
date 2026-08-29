package com.dndmaster.gmeval.domain;
import java.util.*;
public record EvalCase(String caseId, int schemaVersion, String playerInput, EvalContext context,
                       List<HardExpectation> hardExpectations, List<QualityRubric> rubrics) {
    public EvalCase { if (caseId == null || caseId.isBlank() || schemaVersion != 1 || playerInput == null || playerInput.isBlank() || context == null) throw new IllegalArgumentException("invalid EvalCase");
        hardExpectations = List.copyOf(hardExpectations == null ? List.of() : hardExpectations); rubrics = List.copyOf(rubrics == null ? List.of() : rubrics);
        if (hardExpectations.stream().map(HardExpectation::id).distinct().count() != hardExpectations.size()) throw new IllegalArgumentException("expectation IDs must be unique");
        if (rubrics.stream().map(QualityRubric::dimension).distinct().count() != rubrics.size()) throw new IllegalArgumentException("rubric dimensions must be unique"); }
}
