package com.dndmaster.gmeval;

import static org.junit.jupiter.api.Assertions.*;

import com.dndmaster.gmeval.domain.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EvalModelTest {
    @Test void evaluatesHardExpectationsAndKeepsQualitySeparate() {
        EvalCase c = new EvalCase("case-1", 1, "What do I see?",
                new EvalContext(Map.of("weather", "clear"), Map.of("door", "closed"),
                        List.of("the moon is red"), "arrival", null, null),
                List.of(new HardExpectation.ForbiddenFact("secret", "secret-1", "the moon is red"),
                        new HardExpectation.RequiredFact("fact", "fact-1", "door is closed"),
                        new HardExpectation.Unsupported("future", "future-1", "not deterministic")), List.of());
        EvalResult result = new AbsoluteEvaluationService().evaluate(c, "The moon is red. The door is closed.");
        assertEquals(List.of(HardStatus.FAIL, HardStatus.PASS, HardStatus.UNEVALUATED),
                result.hardResults().stream().map(HardConstraintResult::status).toList());
        assertTrue(result.qualityScores().isEmpty());
    }

    @Test void rejectsInvalidCaseAndRubric() {
        assertThrows(IllegalArgumentException.class, () -> new EvalCase("", 1, "input",
                EvalContext.empty(), List.of(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new EvalCase("id", 2, "input",
                EvalContext.empty(), List.of(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new QualityRubric("clarity", Map.of(1, "")));
    }

    @Test void evaluatesStateMutationAndExplicitRuleContradiction() {
        EvalCase c = new EvalCase("state", 1, "act", new EvalContext(Map.of("door", "closed"), Map.of(), List.of(), "start", null, null),
                List.of(new HardExpectation.StateMutation("state", "s", "door", "closed"),
                        new HardExpectation.RuleContradiction("rule", "r", "door is closed")), List.of());
        EvalResult result = new AbsoluteEvaluationService().evaluate(c, "The door is open.");
        assertEquals(List.of(HardStatus.FAIL, HardStatus.FAIL), result.hardResults().stream().map(HardConstraintResult::status).toList());
    }

    @Test void doesNotTreatNegatedFactsAsDirectFacts() {
        EvalCase c = new EvalCase("negation", 1, "look", EvalContext.empty(),
                List.of(new HardExpectation.RequiredFact("fact", "required", "door is closed"),
                        new HardExpectation.ForbiddenFact("fact", "forbidden", "moon is red")), List.of());
        EvalResult result = new AbsoluteEvaluationService().evaluate(c, "The door is not closed and the moon is not red.");
        assertEquals(List.of(HardStatus.FAIL, HardStatus.PASS), result.hardResults().stream().map(HardConstraintResult::status).toList());
    }
}
