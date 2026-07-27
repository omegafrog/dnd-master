package com.dndmaster.aigamemaster.application.rule;
import java.util.*;
public record RuleAnswerOutput(EvidenceStatus evidenceStatus,String conclusion,List<Citation> conclusionCitations,List<RuleCandidate> candidates,boolean uncertaintyDisclosed){public RuleAnswerOutput{Objects.requireNonNull(evidenceStatus);conclusionCitations=List.copyOf(Objects.requireNonNull(conclusionCitations));candidates=List.copyOf(Objects.requireNonNull(candidates));}}
