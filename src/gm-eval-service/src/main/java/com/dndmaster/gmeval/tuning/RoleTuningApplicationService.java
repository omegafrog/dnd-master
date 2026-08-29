package com.dndmaster.gmeval.tuning;

import com.dndmaster.gmeval.registry.PromptRole;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** Routes only eligible proposals to the provider registered for that role. */
public final class RoleTuningApplicationService {
    private final TuningReadinessGate readinessGate;
    private final Map<PromptRole, RoleTuningPort> trainers;
    private final TrainingArtifactRepository artifacts;

    public RoleTuningApplicationService(TuningReadinessGate readinessGate, Map<PromptRole, RoleTuningPort> trainers,
                                        TrainingArtifactRepository artifacts) {
        this.readinessGate = Objects.requireNonNull(readinessGate, "tuning readiness gate required");
        this.trainers = new EnumMap<>(PromptRole.class);
        this.trainers.putAll(Objects.requireNonNull(trainers, "role trainers required"));
        this.trainers.forEach((role, trainer) -> {
            if (trainer == null || trainer.role() != role) throw new IllegalArgumentException("trainer role mismatch");
        });
        this.artifacts = Objects.requireNonNull(artifacts, "training artifact repository required");
    }

    public TrainingArtifact train(TuningProposal proposal, TrainingHyperparameters hyperparameters) {
        TuningEligibility eligibility = readinessGate.evaluate(Objects.requireNonNull(proposal, "tuning proposal required"));
        if (!eligibility.eligible()) throw new TuningNotEligibleException(eligibility);
        RoleTuningPort trainer = trainers.get(proposal.role());
        if (trainer == null) throw new IllegalArgumentException("no trainer registered for role " + proposal.role());
        TrainingArtifact artifact = Objects.requireNonNull(trainer.train(new TuningTrainingRequest(proposal, eligibility, hyperparameters)),
                "trainer artifact required");
        validateArtifact(proposal, artifact);
        artifacts.save(artifact);
        return artifact;
    }

    private static void validateArtifact(TuningProposal proposal, TrainingArtifact artifact) {
        if (!artifact.proposalId().equals(proposal.proposalId()) || artifact.role() != proposal.role()
                || !artifact.baseModelVersion().equals(proposal.baseModelVersion())
                || !artifact.optimizedPromptVersion().equals(proposal.optimizedPromptVersion())
                || !artifact.datasetVersion().equals(proposal.datasetVersion())
                || !artifact.holdoutVersion().equals(proposal.holdoutVersion())
                || artifact.hyperparameters().method() != proposal.method()) {
            throw new IllegalArgumentException("training artifact identity mismatch");
        }
    }
}
