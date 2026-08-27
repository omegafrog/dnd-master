package com.dndmaster.aigamemaster.application.rule;
import java.util.*;
public record SourceEvidence(UUID rulebookId,String locator,String excerpt,String citationKey){
 public SourceEvidence(UUID rulebookId,String locator,String excerpt){this(rulebookId,locator,excerpt,null);}
 public SourceEvidence{Objects.requireNonNull(rulebookId);if(locator==null||locator.isBlank()||excerpt==null||excerpt.isBlank())throw new IllegalArgumentException("evidence fields required");locator=locator.trim();excerpt=excerpt.trim();citationKey=citationKey==null||citationKey.isBlank()?null:citationKey.trim();}
 public Citation citation(){return new Citation(rulebookId,locator,citationKey);}
}
