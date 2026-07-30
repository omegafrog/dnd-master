package com.dndmaster.aigamemaster.application.scene;
import java.util.UUID;
public record ScenarioPrompt(String value, UUID scenarioId, UUID ruleSetId){public ScenarioPrompt{if(value==null||value.isBlank())throw new IllegalArgumentException("prompt required");if(scenarioId==null||ruleSetId==null)throw new IllegalArgumentException("scenario and rule set ids required");}}
