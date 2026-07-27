package com.dndmaster.combatmap.application.view;
import java.util.Objects; import java.util.UUID;
public record MapOwnerId(UUID value){public MapOwnerId{Objects.requireNonNull(value);}}
