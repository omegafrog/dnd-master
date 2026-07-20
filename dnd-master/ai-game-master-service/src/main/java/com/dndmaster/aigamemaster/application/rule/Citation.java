package com.dndmaster.aigamemaster.application.rule;
import java.util.*;
public record Citation(UUID rulebookId,String locator){public Citation{Objects.requireNonNull(rulebookId);if(locator==null||locator.isBlank())throw new IllegalArgumentException("locator required");locator=locator.trim();}}
