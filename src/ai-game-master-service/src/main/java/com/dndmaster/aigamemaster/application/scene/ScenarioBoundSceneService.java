package com.dndmaster.aigamemaster.application.scene;

import com.dndmaster.aigamemaster.application.GroundingViolationException;
import java.util.Objects;

/** Keeps canonical scenario truth bounded while allowing ordinary runtime dialogue. */
public final class ScenarioBoundSceneService {
    private final ScenarioPromptFactory prompts;
    private final SceneModelPort model;

    public ScenarioBoundSceneService(ScenarioPromptFactory prompts, SceneModelPort model) {
        this.prompts = Objects.requireNonNull(prompts, "prompt factory must not be null");
        this.model = Objects.requireNonNull(model, "scene model must not be null");
    }

    public SceneOutput generate(ScenarioRequest request) {
        Objects.requireNonNull(request, "scenario request must not be null");
        SceneOutput output = model.generateScene(prompts.create(request));
        if (!output.scenarioId().equals(request.scenarioId())
                || !output.ruleSetId().equals(request.ruleSetId())) {
            throw new GroundingViolationException("model output referenced unselected scenario or rule set");
        }
        if (output.alignment() == ScenarioAlignment.ORIGINAL_EXPANSION) {
            throw new GroundingViolationException("canonical scenario expansion is forbidden");
        }
        String grounded = SceneCitationGrounder.groundOrFallback(output.scene(), request,
                output.alignment() == ScenarioAlignment.RUNTIME_INTERACTION);
        grounded = SceneChoicePolicy.replaceImmediateRepeat(grounded, request);
        return new SceneOutput(output.scenarioId(), output.ruleSetId(), output.alignment(), grounded, output.npcs());
    }
}
