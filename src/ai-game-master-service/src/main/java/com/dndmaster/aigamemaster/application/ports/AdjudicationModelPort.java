package com.dndmaster.aigamemaster.application.ports;
public interface AdjudicationModelPort{AdjudicationOutput adjudicate(AdjudicationInput input);record AdjudicationInput(String action,String context,String ruleSetId){}record AdjudicationOutput(String outcome,String ruleBasis){}}
