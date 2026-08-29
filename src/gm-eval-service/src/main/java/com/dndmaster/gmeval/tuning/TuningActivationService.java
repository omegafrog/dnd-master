package com.dndmaster.gmeval.tuning;

import com.dndmaster.gmeval.registry.PromptRegistryReadPort;
import com.dndmaster.gmeval.registry.PromptRole;
import com.dndmaster.gmeval.registry.PromptVersion;
import java.util.Objects;

/** Connects approved GMQ-003 prompt configuration to role-scoped tuned model activation. */
public final class TuningActivationService {
    private final PromptRegistryReadPort prompts;
    private final TunedModelRegistry models;

    public TuningActivationService(PromptRegistryReadPort prompts, TunedModelRegistry models) {
        this.prompts = Objects.requireNonNull(prompts, "prompt registry required");
        this.models = Objects.requireNonNull(models, "tuned model registry required");
    }

    public RoleModelConfiguration activate(TuningEvaluationReport report, String expectedActiveModelVersion, String actor) {
        Objects.requireNonNull(report, "tuning evaluation report required");
        if (!report.gateReport().passed()) throw new TuningActivationException("tuning gates did not pass: " + report.gateReport().failures());
        if (!prompts.active(report.role()).promptVersion().value().equals(report.artifact().optimizedPromptVersion())) {
            throw new TuningActivationException("optimized prompt is not active for role " + report.role());
        }
        RoleModelConfiguration candidate = new RoleModelConfiguration(report.role(), report.artifact().tunedModelVersion(),
                new PromptVersion(report.role(), report.artifact().optimizedPromptVersion()), report.artifact().artifactId(),
                report.evaluationId(), report.lineageDelta());
        return models.activate(candidate, expectedActiveModelVersion, actor);
    }

    public RoleModelConfiguration rollback(PromptRole role, String targetModelVersion,
                                           String expectedActiveModelVersion, String actor) {
        return models.rollback(role, targetModelVersion, expectedActiveModelVersion, actor);
    }
}
