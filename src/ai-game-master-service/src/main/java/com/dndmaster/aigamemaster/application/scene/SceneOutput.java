package com.dndmaster.aigamemaster.application.scene;
import java.util.*;
public record SceneOutput(UUID scenarioId,UUID ruleSetId,ScenarioAlignment alignment,String scene,List<NpcOutput> npcs){public SceneOutput{Objects.requireNonNull(scenarioId);Objects.requireNonNull(ruleSetId);Objects.requireNonNull(alignment);if(scene==null||scene.isBlank())throw new IllegalArgumentException("scene required");npcs=List.copyOf(Objects.requireNonNull(npcs));}}
