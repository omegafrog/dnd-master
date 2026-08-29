package com.dndmaster.gmeval.domain;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Validates the complete, exact structured judge contract before scores enter the result. */
public final class RubricJudgeResponseValidator {
    private RubricJudgeResponseValidator() { }

    public static List<QualityScore> validate(List<QualityRubric> rubrics, RubricJudgeResponse response) {
        if (response == null) throw new IllegalArgumentException("judge response required");
        List<QualityRubric> requested = rubrics == null ? List.of() : rubrics;
        List<QualityScore> scores = response.scores();
        if (scores.size() != requested.size()) throw new IllegalArgumentException("judge dimensions do not match request");
        Set<String> expected = requested.stream().map(QualityRubric::dimension).collect(java.util.stream.Collectors.toSet());
        Set<String> actual = new HashSet<>();
        for (QualityScore score : scores) {
            if (score == null || score.dimension() == null || score.dimension().isBlank()
                    || !expected.contains(score.dimension()) || !actual.add(score.dimension()))
                throw new IllegalArgumentException("invalid judge dimension");
            if (score.score() < 1 || score.score() > 5) throw new IllegalArgumentException("judge score out of range");
            if (score.reason() == null || score.reason().isBlank()) throw new IllegalArgumentException("judge reason required");
            if (score.evidence() == null || score.evidence().isBlank()) throw new IllegalArgumentException("judge evidence required");
        }
        if (!actual.equals(expected)) throw new IllegalArgumentException("judge dimensions do not match request");
        return List.copyOf(scores);
    }
}
