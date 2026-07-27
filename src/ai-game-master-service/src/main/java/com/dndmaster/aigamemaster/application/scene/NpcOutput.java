package com.dndmaster.aigamemaster.application.scene;
public record NpcOutput(String name,String action){public NpcOutput{if(name==null||name.isBlank()||action==null||action.isBlank())throw new IllegalArgumentException("NPC output required");}}
