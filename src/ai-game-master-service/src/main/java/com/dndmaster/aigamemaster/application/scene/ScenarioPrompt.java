package com.dndmaster.aigamemaster.application.scene;
import java.util.UUID;
public record ScenarioPrompt(String value, UUID soloPlayerId, UUID scenarioId, UUID ruleSetId){public ScenarioPrompt{if(value==null||value.isBlank())throw new IllegalArgumentException("prompt required");if(soloPlayerId==null||scenarioId==null||ruleSetId==null)throw new IllegalArgumentException("solo player, scenario, and rule set ids required");}}
