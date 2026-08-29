package com.dndmaster.gmeval.domain;
import java.util.*;
public final class PairwiseJudgeResponseValidator {
    private PairwiseJudgeResponseValidator() { }
    public static PairwiseJudgeResponse validate(List<QualityRubric> rubrics, PairwiseJudgeResponse response) {
        if (response == null || response.winner() == null) throw new IllegalArgumentException("pairwise winner required");
        if (response.reason() == null || response.reason().isBlank() || response.evidence() == null || response.evidence().isBlank()) throw new IllegalArgumentException("pairwise reason and evidence required");
        List<PairwiseDimensionPreference> prefs = response.preferences();
        Set<String> expected = new HashSet<>(); for (QualityRubric r : rubrics == null ? List.<QualityRubric>of() : rubrics) expected.add(r.dimension());
        if (prefs.size() != expected.size()) throw new IllegalArgumentException("pairwise dimensions do not match request");
        Set<String> actual = new HashSet<>(); for (PairwiseDimensionPreference p : prefs) if (!expected.contains(p.dimension()) || !actual.add(p.dimension())) throw new IllegalArgumentException("invalid pairwise dimension");
        if (!actual.equals(expected)) throw new IllegalArgumentException("pairwise dimensions do not match request");
        return new PairwiseJudgeResponse(response.winner(), prefs, response.reason(), response.evidence());
    }
}
