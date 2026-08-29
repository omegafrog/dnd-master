package com.dndmaster.gmeval;

import static org.junit.jupiter.api.Assertions.*;

import com.dndmaster.gmeval.domain.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RubricJudgeTest {
    private static final QualityRubric CLARITY = new QualityRubric("clarity",
            Map.of(1, "incoherent", 2, "weak", 3, "adequate", 4, "clear", 5, "exceptional"));

    private EvalCase caseWithRubric() {
        return new EvalCase("case-judge", 1, "look", EvalContext.empty(), List.of(), List.of(CLARITY));
    }

    @Test void validJudgeOutputBecomesStructuredScores() {
        RubricJudgePort judge = request -> new RubricJudgeResponse(List.of(
                new QualityScore("clarity", 4, "The scene is easy to follow.", "The first paragraph establishes the room.")));
        EvalResult result = new AbsoluteEvaluationService(judge).evaluate(caseWithRubric(), "A clear scene.");
        assertEquals(4, result.qualityScores().getFirst().score());
        assertFalse(result.qualityJudgeFailed());
    }

    @Test void missingEvidenceFailsClosedWithoutFabricatedScore() {
        RubricJudgePort judge = request -> new RubricJudgeResponse(List.of(
                new QualityScore("clarity", 5, "great", "")));
        EvalResult result = new AbsoluteEvaluationService(judge).evaluate(caseWithRubric(), "response");
        assertTrue(result.qualityScores().isEmpty());
        assertTrue(result.qualityJudgeFailed());
    }

    @Test void missingDimensionAndExtraDimensionAreRejected() {
        assertThrows(IllegalArgumentException.class, () ->
                RubricJudgeResponseValidator.validate(List.of(CLARITY),
                        new RubricJudgeResponse(List.of())));
        assertThrows(IllegalArgumentException.class, () ->
                RubricJudgeResponseValidator.validate(List.of(CLARITY),
                        new RubricJudgeResponse(List.of(
                                new QualityScore("clarity", 3, "ok", "evidence"),
                                new QualityScore("pacing", 3, "ok", "evidence")))));
    }

    @Test void highQualityDoesNotChangeHardFailure() {
        EvalCase c = new EvalCase("case-hard", 1, "look", EvalContext.empty(),
                List.of(new HardExpectation.ForbiddenFact("information", "secret", "dragon")), List.of(CLARITY));
        AbsoluteEvaluationService service = new AbsoluteEvaluationService(request -> new RubricJudgeResponse(List.of(
                new QualityScore("clarity", 5, "excellent", "specific evidence"))));
        EvalResult result = service.evaluate(c, "The dragon is here.");
        assertEquals(HardStatus.FAIL, result.hardResults().getFirst().status());
        assertEquals(5, result.qualityScores().getFirst().score());
    }
}
