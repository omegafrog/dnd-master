package com.dndmaster.gmeval.infrastructure;

import com.dndmaster.gmeval.domain.EvalCase;
import java.util.*;

/** Contract checks for the published seed dataset; small fixtures can use the loader alone. */
public final class EvalDatasetIntegrity {
    private EvalDatasetIntegrity() {}
    public static void validateSeed(List<EvalCase> cases) {
        if (cases == null || cases.size() < 30 || cases.size() > 50) throw new IllegalArgumentException("seed dataset must contain 30-50 cases");
        Set<String> ids = new HashSet<>(), categories = new HashSet<>();
        for (EvalCase c : cases) { if (!ids.add(c.caseId()) || c.schemaVersion() != 1) throw new IllegalArgumentException("invalid seed case identity/version: " + c.caseId()); c.hardExpectations().forEach(e -> categories.add(e.category().toLowerCase(Locale.ROOT))); c.rubrics().forEach(r -> categories.add(r.dimension().toLowerCase(Locale.ROOT))); }
        for (String required : List.of("rule", "information", "state", "agency", "continuity", "quality")) if (categories.stream().noneMatch(c -> c.contains(required))) throw new IllegalArgumentException("missing seed category: " + required);
    }
}
