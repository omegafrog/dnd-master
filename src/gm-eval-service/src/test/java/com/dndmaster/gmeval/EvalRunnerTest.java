package com.dndmaster.gmeval;

import static org.junit.jupiter.api.Assertions.*;
import com.dndmaster.gmeval.application.*;
import com.dndmaster.gmeval.domain.*;
import com.dndmaster.gmeval.infrastructure.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class EvalRunnerTest {
    private EvalCase c(String id, String category) { return new EvalCase(id, 1, "look", EvalContext.empty(), List.of(new HardExpectation.Unsupported(category, "h-" + id, "judge")), List.of(new QualityRubric(category, Map.of(1,"poor",2,"weak",3,"okay",4,"good",5,"great")))); }
    @Test void aggregatesExcludesUnevaluatedFromHardPassRateAndPersistsMetadata() throws Exception {
        EvalRunConfiguration config = new EvalRunConfiguration("run-1", "gm-turn-v1", "fake-model", "prompt-7", "config-2", null, null);
        EvalRunner runner = new EvalRunner(new AbsoluteEvaluationService(request -> new RubricJudgeResponse(List.of(new QualityScore("quality", 4, "clear", "line")))), null);
        EvalRunReport report = runner.run(List.of(c("one", "quality")), config, (x, y) -> new GeneratedResponse("response", "fixture"), null);
        assertEquals(1, report.aggregate().caseCount());
        assertEquals(0, report.aggregate().hardPassCount());
        assertEquals(1, report.aggregate().hardUnevaluatedCount());
        assertEquals(4.0, report.aggregate().qualityAverageByCategory().get("quality"));
        Path out = Files.createTempFile("eval-report", ".json");
        new JsonEvalReportWriter().write(report, out);
        String json = Files.readString(out);
        assertTrue(json.contains("run-1") && json.contains("gm-turn-v1") && json.contains("hardUnevaluatedCount"));
    }
    @Test void generatorFailureIsRecordedAndDoesNotAbortFollowingCases() {
        EvalRunConfiguration config = new EvalRunConfiguration("run-2", "v1", "fake", "p", "c", null, null);
        EvalRunner runner = new EvalRunner(null, null);
        EvalRunReport report = runner.run(List.of(c("bad", "quality"), c("good", "quality")), config,
                (x, y) -> { if (x.caseId().equals("bad")) throw new IllegalStateException("provider unavailable"); return new GeneratedResponse("ok", "fixture"); }, null);
        assertEquals("provider unavailable", report.cases().getFirst().generatorFailure());
        assertEquals("good", report.cases().get(1).caseId());
    }
    @Test void seedHasPinnedSizeAndAllCategories() throws Exception {
        List<EvalCase> cases = new JsonlEvalDatasetLoader().loadResource("eval/datasets/gm-turn-v1.jsonl");
        assertDoesNotThrow(() -> EvalDatasetIntegrity.validateSeed(cases));
        assertEquals(48, cases.size());
    }
}
