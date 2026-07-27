package com.dndmaster.aigamemaster.application.rule;
import java.util.*;
public record SourceEvidence(UUID rulebookId,String locator,String excerpt){public SourceEvidence{Objects.requireNonNull(rulebookId);if(locator==null||locator.isBlank()||excerpt==null||excerpt.isBlank())throw new IllegalArgumentException("evidence fields required");locator=locator.trim();excerpt=excerpt.trim();}public Citation citation(){return new Citation(rulebookId,locator);}}
