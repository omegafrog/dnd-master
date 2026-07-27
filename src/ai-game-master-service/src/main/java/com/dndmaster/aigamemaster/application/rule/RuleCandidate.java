package com.dndmaster.aigamemaster.application.rule;
import java.util.*;
public record RuleCandidate(String content,List<Citation> citations){public RuleCandidate{if(content==null||content.isBlank())throw new IllegalArgumentException("candidate required");citations=List.copyOf(Objects.requireNonNull(citations));}}
