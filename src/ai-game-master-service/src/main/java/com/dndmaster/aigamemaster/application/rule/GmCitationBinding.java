package com.dndmaster.aigamemaster.application.rule;
import java.util.Objects;
public record GmCitationBinding(String claimText, String outputField, String citationKey) {
 public GmCitationBinding {
  claimText=required(claimText,"claim text"); outputField=required(outputField,"output field"); citationKey=required(citationKey,"citation key");
 }
 private static String required(String value,String name){if(value==null||value.isBlank())throw new IllegalArgumentException(name+" required");return value.trim();}
}
