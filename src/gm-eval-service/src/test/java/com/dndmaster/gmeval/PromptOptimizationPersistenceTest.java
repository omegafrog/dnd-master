package com.dndmaster.gmeval;

import static org.junit.jupiter.api.Assertions.*;

import com.dndmaster.gmeval.optimization.*;
import com.dndmaster.gmeval.registry.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PromptOptimizationPersistenceTest {
    @Test
    void persistsAndReadsCandidateMetricsBaselineDeltaAndReportProjection() throws Exception {
        PromptArtifact baseline = artifact("1.0.0", true);
        PromptCandidate candidate = new PromptCandidate("writer-candidate", artifact("1.1.0", false), 42);
        MetricVector baselineMetrics = new MetricVector(Map.of(), Map.of("coherence", 3.0));
        MetricVector candidateMetrics = new MetricVector(Map.of(), Map.of("coherence", 4.0));
        PromptCandidateEvaluation evaluated = PromptCandidateEvaluation.from(candidate, candidateMetrics,
                BaselineDelta.between(candidateMetrics, baselineMetrics), List.of("A guarded output."));
        PromptOptimizationRun run = new PromptOptimizationRun("run-42", PromptRole.WRITER, "dataset-v1", "eval-v1", 42,
                baseline, List.of(evaluated), evaluated);
        Path file = Files.createTempFile("prompt-optimization", ".json");
        PromptOptimizationRunRepository repository = new PromptOptimizationRunRepository(new JsonPromptOptimizationRunStore(file));

        repository.save(run);
        PromptOptimizationRun restored = repository.find("run-42").orElseThrow();

        assertEquals(run.report(), restored.report());
        assertEquals(1, restored.report().candidates().size());
        assertEquals(1.0, restored.report().candidates().getFirst().baselineDelta().softScoreDelta().get("coherence"));
        assertEquals("writer-candidate", repository.readProjection("run-42").selectedCandidateId());
    }

    private static PromptArtifact artifact(String version, boolean baseline) {
        return new PromptArtifact(new PromptVersion(PromptRole.WRITER, version), null, "prompt-" + version,
                "schema-1", List.of("context"), "after-context", "model-1", "config-1",
                "dataset-v1", "eval-v1", baseline, PromptArtifactStatus.DRAFT);
    }
}
