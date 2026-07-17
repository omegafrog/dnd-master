package com.dndmaster.aigamemaster.application.scene;
public record ScenarioPrompt(String value){public ScenarioPrompt{if(value==null||value.isBlank())throw new IllegalArgumentException("prompt required");}}
