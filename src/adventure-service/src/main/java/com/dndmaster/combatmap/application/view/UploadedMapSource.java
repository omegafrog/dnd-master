package com.dndmaster.combatmap.application.view;
import java.util.Objects;
public record UploadedMapSource(String filename,byte[] content){public UploadedMapSource{if(filename==null||filename.isBlank())throw new IllegalArgumentException("filename required");Objects.requireNonNull(content);if(content.length==0)throw new IllegalArgumentException("content required");filename=filename.trim();content=content.clone();}@Override public byte[] content(){return content.clone();}}
