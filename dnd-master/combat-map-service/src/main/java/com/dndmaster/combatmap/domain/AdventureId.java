package com.dndmaster.combatmap.domain;
import java.util.Objects; import java.util.UUID;
public record AdventureId(UUID value) { public AdventureId { Objects.requireNonNull(value); } }
