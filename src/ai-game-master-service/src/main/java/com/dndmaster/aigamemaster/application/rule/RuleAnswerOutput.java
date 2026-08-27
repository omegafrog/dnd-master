package com.dndmaster.aigamemaster.application.rule;
import java.util.*;
public record RuleAnswerOutput(EvidenceStatus evidenceStatus,String conclusion,List<Citation> conclusionCitations,List<RuleCandidate> candidates,boolean uncertaintyDisclosed,List<GmCitationBinding> citationBindings){
 public RuleAnswerOutput(EvidenceStatus status,String conclusion,List<Citation> citations,List<RuleCandidate> candidates,boolean disclosed){this(status,conclusion,citations,candidates,disclosed,List.of());}
 public RuleAnswerOutput{Objects.requireNonNull(evidenceStatus);conclusionCitations=List.copyOf(Objects.requireNonNull(conclusionCitations));candidates=List.copyOf(Objects.requireNonNull(candidates));citationBindings=List.copyOf(Objects.requireNonNull(citationBindings));}
}
