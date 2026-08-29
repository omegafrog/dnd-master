package com.dndmaster.gmeval;

import static org.junit.jupiter.api.Assertions.*;

import com.dndmaster.gmeval.domain.*;
import com.dndmaster.gmeval.infrastructure.AiGmPairwiseJudgeAdapter;
import com.dndmaster.gmeval.infrastructure.StructuredOutputCompletionPort;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PairwiseEvaluationTest {
    private static final QualityRubric CLARITY = new QualityRubric("clarity",
            Map.of(1, "incoherent", 2, "weak", 3, "adequate", 4, "clear", 5, "exceptional"));

    private EvalCase caseOf(String id) {
        return new EvalCase(id, 1, "look", EvalContext.empty(),
                List.of(new HardExpectation.ForbiddenFact("information", "secret", "dragon")), List.of(CLARITY));
    }

    private PairwiseJudgeResponse response(PairwiseWinner winner) {
        return new PairwiseJudgeResponse(winner, List.of(
                new PairwiseDimensionPreference("clarity", PairwisePreference.A, "A is clearer", "A explains the room")),
                "A is preferable overall", "A gives concrete detail");
    }

    @Test void comparesSameCaseAndPreservesIndependentHardResults() {
        PairwiseEvaluationService service = new PairwiseEvaluationService(
                new AbsoluteEvaluationService(), request -> response(PairwiseWinner.A));
        PairwiseEvalResult result = service.compare(caseOf("case-1"), "The dragon is here.", "A dark room.");
        assertEquals(PairwiseWinner.A, result.winner());
        assertEquals(HardStatus.FAIL, result.responseA().hardResults().getFirst().status());
        assertEquals(HardStatus.PASS, result.responseB().hardResults().getFirst().status());
        assertEquals(PairwisePreference.A, result.preferences().getFirst().preference());
    }

    @Test void rejectsDifferentCaseIdentityOrVersion() {
        PairwiseEvaluationService service = new PairwiseEvaluationService(
                new AbsoluteEvaluationService(), request -> response(PairwiseWinner.TIE));
        assertThrows(IllegalArgumentException.class, () -> service.compare(caseOf("case-1"),
                new PairwiseResponse("case-2", 1, "a"), new PairwiseResponse("case-1", 1, "b")));
        assertThrows(IllegalArgumentException.class, () -> service.compare(caseOf("case-1"),
                new PairwiseResponse("case-1", 2, "a"), new PairwiseResponse("case-1", 1, "b")));
    }

    @Test void malformedPairwiseJudgeIsFailureNotWinner() {
        PairwiseEvaluationService service = new PairwiseEvaluationService(new AbsoluteEvaluationService(), request ->
                new PairwiseJudgeResponse(PairwiseWinner.A, List.of(), "", ""));
        PairwiseEvalResult result = service.compare(caseOf("case-1"), "a", "b");
        assertNull(result.winner());
        assertTrue(result.judgeFailed());
        assertTrue(result.preferences().isEmpty());
    }

    @Test void adapterAcceptsCanonicalAndRejectsMissingOrExtraDimensions() {
        StructuredOutputCompletionPort canonical = prompt ->
                "{\"winner\":\"B\",\"reason\":\"B wins\",\"evidence\":\"more detail\",\"preferences\":[{\"dimension\":\"clarity\",\"preference\":\"B\",\"reason\":\"clearer\",\"evidence\":\"second paragraph\"}]}";
        PairwiseJudgeResponse parsed = new AiGmPairwiseJudgeAdapter(canonical).compare(
                new PairwiseJudgeRequest(caseOf("case-1"), "a", "b", List.of(CLARITY)));
        assertEquals(PairwiseWinner.B, parsed.winner());
        assertEquals(PairwisePreference.B, parsed.preferences().getFirst().preference());

        StructuredOutputCompletionPort malformed = prompt ->
                "{\"winner\":\"A\",\"reason\":\"x\",\"evidence\":\"x\",\"preferences\":[]}";
        assertThrows(IllegalArgumentException.class, () -> new AiGmPairwiseJudgeAdapter(malformed).compare(
                new PairwiseJudgeRequest(caseOf("case-1"), "a", "b", List.of(CLARITY))));
    }
}
