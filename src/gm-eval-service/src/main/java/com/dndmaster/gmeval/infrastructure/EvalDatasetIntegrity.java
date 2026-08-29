package com.dndmaster.gmeval.infrastructure;

import com.dndmaster.gmeval.domain.EvalCase;
import java.util.*;

/** Contract checks for the published seed dataset; small fixtures can use the loader alone. */
public final class EvalDatasetIntegrity {
    private EvalDatasetIntegrity() {}
    public static void validateSeed(List<EvalCase> cases) {
        if (cases == null || cases.size() < 30 || cases.size() > 50) throw new IllegalArgumentException("seed dataset must contain 30-50 cases");
        Set<String> ids = new HashSet<>(); Map<String,Integer> counts = new HashMap<>();
        for (EvalCase c : cases) { if (!ids.add(c.caseId()) || c.schemaVersion() != 1) throw new IllegalArgumentException("invalid seed case identity/version: " + c.caseId()); c.hardExpectations().forEach(e -> counts.merge(e.category().toLowerCase(Locale.ROOT), 1, Integer::sum)); c.rubrics().forEach(r -> counts.merge(r.dimension().toLowerCase(Locale.ROOT), 1, Integer::sum)); }
        for (String required : List.of("rule", "information", "state", "agency", "continuity", "quality")) if (counts.entrySet().stream().filter(e -> e.getKey().contains(required)).mapToInt(Map.Entry::getValue).sum() < 6) throw new IllegalArgumentException("insufficient seed category coverage: " + required);
    }
}
