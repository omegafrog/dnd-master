package com.dndmaster.aigamemaster.application.rule;
import java.util.*;
public record Citation(UUID rulebookId,String locator,String citationKey){
 public Citation(UUID rulebookId,String locator){this(rulebookId,locator,null);}
 public Citation{Objects.requireNonNull(rulebookId);if(locator==null||locator.isBlank())throw new IllegalArgumentException("locator required");locator=locator.trim();citationKey=citationKey==null||citationKey.isBlank()?null:citationKey.trim();}
}
