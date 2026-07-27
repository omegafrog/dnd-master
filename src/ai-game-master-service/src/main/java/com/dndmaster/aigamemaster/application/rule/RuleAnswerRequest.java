package com.dndmaster.aigamemaster.application.rule;
import java.util.*;
public record RuleAnswerRequest(UUID ruleSetId,String situation,EvidenceStatus evidenceStatus,List<SourceEvidence> evidence){public RuleAnswerRequest{Objects.requireNonNull(ruleSetId);if(situation==null||situation.isBlank())throw new IllegalArgumentException("situation required");Objects.requireNonNull(evidenceStatus);evidence=List.copyOf(Objects.requireNonNull(evidence));}}
