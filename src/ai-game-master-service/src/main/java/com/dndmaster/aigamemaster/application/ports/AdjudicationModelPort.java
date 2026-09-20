package com.dndmaster.aigamemaster.application.ports;
import java.util.Objects;
import java.util.UUID;
public interface AdjudicationModelPort{AdjudicationOutput adjudicate(AdjudicationInput input);record AdjudicationInput(UUID soloPlayerId,String action,String context,String ruleSetId){public AdjudicationInput{Objects.requireNonNull(soloPlayerId,"soloPlayerId is required");}}record AdjudicationOutput(String outcome,String ruleBasis){}}
